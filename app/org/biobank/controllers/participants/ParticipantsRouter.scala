package org.biobank.controllers.participants

import javax.inject.Inject
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

class ParticipantsRouter @Inject()(controller: ParticipantsController) extends SimpleRouter {
  import ParticipantsRouting._
  import org.biobank.controllers.study.StudiesRouting._

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  override def routes: Routes = {

    case GET(p"/uniqueId/${studyId(sId)}/$uniqueId") =>
      controller.getByUniqueId(sId, uniqueId)

    case GET(p"/${studyId(sId)}/${participantId(id)}") =>
      controller.get(sId, id)

    case POST(p"/${studyId(sId)}") =>
      controller.add(sId)

    case POST(p"/uniqueId/${participantId(id)}") =>
      controller.updateUniqueId(id)

    case POST(p"/annot/${participantId(id)}") =>
      controller.addAnnotation(id)

    case DELETE(p"/annot/${participantId(id)}/$annotTypeId/${long(ver)}") =>
      controller.removeAnnotation(id, annotTypeId, ver)


  }
}
