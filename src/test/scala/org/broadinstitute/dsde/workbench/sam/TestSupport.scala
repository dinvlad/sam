package org.broadinstitute.dsde.workbench.sam

import java.net.URI
import java.time.Instant
import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import cats.effect.IO
import cats.kernel.Eq
import com.google.cloud.firestore.{DocumentSnapshot, Firestore, Transaction}
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import org.broadinstitute.dsde.workbench.dataaccess.PubSubNotificationDAO
import org.broadinstitute.dsde.workbench.google.mock.{MockGoogleDirectoryDAO, MockGoogleIamDAO, MockGooglePubSubDAO, MockGoogleStorageDAO}
import org.broadinstitute.dsde.workbench.google.util.DistributedLock
import org.broadinstitute.dsde.workbench.google.{CollectionName, Document, GoogleDirectoryDAO, GoogleFirestoreOps, GoogleIamDAO}
import org.broadinstitute.dsde.workbench.model._
import org.broadinstitute.dsde.workbench.sam.api.StandardUserInfoDirectives._
import org.broadinstitute.dsde.workbench.sam.api._
import org.broadinstitute.dsde.workbench.sam.config.AppConfig._
import org.broadinstitute.dsde.workbench.sam.config._
import org.broadinstitute.dsde.workbench.sam.directory.MockDirectoryDAO
import org.broadinstitute.dsde.workbench.sam.google.{GoogleExtensionRoutes, GoogleExtensions, GoogleKeyCache}
import org.broadinstitute.dsde.workbench.sam.model._
import org.broadinstitute.dsde.workbench.sam.openam.{AccessPolicyDAO, MockAccessPolicyDAO}
import org.broadinstitute.dsde.workbench.sam.service.UserService._
import org.broadinstitute.dsde.workbench.sam.service._
import org.scalatest.Matchers
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.prop.{Configuration, PropertyChecks}
import org.scalatest.time.{Seconds, Span}

import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Awaitable, ExecutionContext}

/**
  * Created by dvoet on 6/27/17.
  */
trait TestSupport{
  def runAndWait[T](f: Awaitable[T]): T = Await.result(f, Duration.Inf)
  implicit val futureTimeout = Timeout(Span(10, Seconds))
  implicit val cs = IO.contextShift(scala.concurrent.ExecutionContext.global)
  implicit val timer = IO.timer(scala.concurrent.ExecutionContext.global)
  implicit val eqWorkbenchException: Eq[WorkbenchException] = (x: WorkbenchException, y: WorkbenchException) => x.getMessage == y.getMessage
}

trait PropertyBasedTesting extends PropertyChecks with Configuration with Matchers {
  implicit override val generatorDrivenConfig: PropertyCheckConfiguration = PropertyCheckConfiguration(minSuccessful = 3)
}

object TestSupport extends TestSupport{
  private val executor = Executors.newCachedThreadPool()
  val blockingEc = ExecutionContext.fromExecutor(executor)
  implicit val eqWorkbenchExceptionErrorReport: Eq[WorkbenchExceptionWithErrorReport] =
    (x: WorkbenchExceptionWithErrorReport, y: WorkbenchExceptionWithErrorReport) =>
      x.errorReport.statusCode == y.errorReport.statusCode && x.errorReport.message == y.errorReport.message
  val config = ConfigFactory.load()
  val appConfig = AppConfig.readConfig(config)
  val petServiceAccountConfig = appConfig.googleConfig.get.petServiceAccountConfig
  val googleServicesConfig = appConfig.googleConfig.get.googleServicesConfig
  val configResourceTypes = config.as[Map[String, ResourceType]]("resourceTypes").values.map(rt => rt.name -> rt).toMap
  val defaultUserEmail = WorkbenchEmail("newuser@new.com")
  val directoryConfig = config.as[DirectoryConfig]("directory")
  val schemaLockConfig = config.as[SchemaLockConfig]("schemaLock")
  val dirURI = new URI(directoryConfig.directoryUrl)

  val testDistributedLock = DistributedLock[IO]("", appConfig.distributedLockConfig, TestGoogleFirestore)
  def proxyEmail(workbenchUserId: WorkbenchUserId) = WorkbenchEmail(s"PROXY_$workbenchUserId@${googleServicesConfig.appsDomain}")
  def googleSubjectIdHeaderWithId(googleSubjectId: GoogleSubjectId) = RawHeader(googleSubjectIdHeader, googleSubjectId.value)
  def genGoogleSubjectId(): GoogleSubjectId = GoogleSubjectId(genRandom(System.currentTimeMillis()))

  def genGoogleSubjectIdHeader = RawHeader(googleSubjectIdHeader, genRandom(System.currentTimeMillis()))
  val defaultEmailHeader = RawHeader(emailHeader, defaultUserEmail.value)
  def genDefaultEmailHeader(workbenchEmail: WorkbenchEmail) = RawHeader(emailHeader, workbenchEmail.value)

  def genSamDependencies(resourceTypes: Map[ResourceTypeName, ResourceType] = Map.empty, googIamDAO: Option[GoogleIamDAO] = None, googleServicesConfig: GoogleServicesConfig = googleServicesConfig, cloudExtensions: Option[CloudExtensions] = None, googleDirectoryDAO: Option[GoogleDirectoryDAO] = None, policyAccessDAO: Option[AccessPolicyDAO] = None)(implicit system: ActorSystem) = {
    val googleDirectoryDAO = new MockGoogleDirectoryDAO()
    val directoryDAO = new MockDirectoryDAO()
    val googleIamDAO = googIamDAO.getOrElse(new MockGoogleIamDAO())
    val policyDAO = policyAccessDAO.getOrElse(new MockAccessPolicyDAO())
    val pubSubDAO = new MockGooglePubSubDAO()
    val googleStorageDAO = new MockGoogleStorageDAO()
    val notificationDAO = new PubSubNotificationDAO(pubSubDAO, "foo")
    val cloudKeyCache = new GoogleKeyCache(googleIamDAO, googleStorageDAO, pubSubDAO, googleServicesConfig, petServiceAccountConfig)
    val googleExt = cloudExtensions.getOrElse(new GoogleExtensions(
      testDistributedLock,
      directoryDAO,
      policyDAO,
      googleDirectoryDAO,
      pubSubDAO,
      googleIamDAO,
      googleStorageDAO,
      null,
      cloudKeyCache,
      notificationDAO,
      googleServicesConfig,
      petServiceAccountConfig,
      resourceTypes))
    val policyEvaluatorService = PolicyEvaluatorService(appConfig.emailDomain, resourceTypes, policyDAO)
    val mockResourceService = new ResourceService(resourceTypes, policyEvaluatorService, policyDAO, directoryDAO, googleExt, "example.com")
    val mockManagedGroupService = new ManagedGroupService(mockResourceService, policyEvaluatorService, resourceTypes, policyDAO, directoryDAO, googleExt, "example.com")

    SamDependencies(mockResourceService, policyEvaluatorService, new UserService(directoryDAO, googleExt), new StatusService(directoryDAO, googleExt), mockManagedGroupService, directoryDAO, policyDAO, googleExt)
  }

  def genSamRoutes(samDependencies: SamDependencies)(implicit system: ActorSystem, materializer: Materializer): SamRoutes = new SamRoutes(samDependencies.resourceService, samDependencies.userService, samDependencies.statusService, samDependencies.managedGroupService, null, samDependencies.directoryDAO, samDependencies.policyEvaluatorService)
    with StandardUserInfoDirectives
    with GoogleExtensionRoutes {
      override val cloudExtensions: CloudExtensions = samDependencies.cloudExtensions
      override val googleExtensions: GoogleExtensions = if(samDependencies.cloudExtensions.isInstanceOf[GoogleExtensions]) samDependencies.cloudExtensions.asInstanceOf[GoogleExtensions] else null
      val googleKeyCache = if(samDependencies.cloudExtensions.isInstanceOf[GoogleExtensions])samDependencies.cloudExtensions.asInstanceOf[GoogleExtensions].googleKeyCache else null
  }

  def genSamRoutesWithDefault(implicit system: ActorSystem, materializer: Materializer): SamRoutes = genSamRoutes(genSamDependencies())
}

final case class SamDependencies(resourceService: ResourceService, policyEvaluatorService: PolicyEvaluatorService, userService: UserService, statusService: StatusService, managedGroupService: ManagedGroupService, directoryDAO: MockDirectoryDAO, policyDao: AccessPolicyDAO, val cloudExtensions: CloudExtensions)

object TestGoogleFirestore extends GoogleFirestoreOps[IO]{
  override def set(
      collectionName: CollectionName,
      document: Document,
      dataMap: Map[String, Any])
    : IO[Instant] = ???
  override def get(
      collectionName: CollectionName,
      document: Document)
    : IO[DocumentSnapshot] = ???
  override def transaction[A](
      ops: (Firestore, Transaction) => IO[A])
    : IO[A] = IO.unit.map(_.asInstanceOf[A]) //create a transaction always finishes so that retrieving lock alway succeeds
}
