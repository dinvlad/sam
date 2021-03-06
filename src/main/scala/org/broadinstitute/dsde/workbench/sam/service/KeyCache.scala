package org.broadinstitute.dsde.workbench.sam.service

import akka.actor.ActorSystem
import org.broadinstitute.dsde.workbench.model.google.ServiceAccountKeyId
import org.broadinstitute.dsde.workbench.model.PetServiceAccount

import scala.concurrent.Future

/**
  * Created by mbemis on 1/10/18.
  */
trait KeyCache {
  def onBoot()(implicit system: ActorSystem): Future[Unit]
  def getKey(pet: PetServiceAccount): Future[String]
  def removeKey(pet: PetServiceAccount, keyId: ServiceAccountKeyId): Future[Unit]
}
