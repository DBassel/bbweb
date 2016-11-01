package org.biobank.controllers.study

import javax.inject.Inject
import play.api.routing.Router.Routes
import play.api.routing.SimpleRouter
import play.api.routing.sird._

class StudiesRouter @Inject()(controller: StudiesController) extends SimpleRouter {
  import StudiesRouting._

  override def routes: Routes = {

    case GET(p"/names" ? q_o"filter=$filter"
               & q_o"order=$order") =>
      controller.listNames(filter, order)

    case GET(p"/counts") =>
      controller.studyCounts

    case GET(p"/valuetypes") =>
      controller.valueTypes

    case GET(p"/anatomicalsrctypes") =>
      controller.anatomicalSourceTypes

    case GET(p"/specimentypes") =>
      controller.specimenTypes

    case GET(p"/preservtypes") =>
      controller.preservTypes

    case GET(p"/preservtemptypes") =>
      controller.preservTempTypes

    case GET(p"/sgvaluetypes") =>
      controller.specimenGroupValueTypes

    case GET(p"/" ? q_o"filter=$filter"
               & q_o"status=$status"
               & q_o"sort=$sort"
               & q_o"page=${int(page)}"
               & q_o"pageSize=${int(pageSize)}"
               & q_o"order=$order") =>
      controller.list(filter, status, sort, page, pageSize, order)

    case POST(p"/") =>
      controller.add

    case GET(p"/${studyId(id)}") =>
      controller.get(id)

    case POST(p"/name/${studyId(id)}") =>
      controller.updateName(id)

    case POST(p"/description/${studyId(id)}") =>
      controller.updateDescription(id)

    case POST(p"/pannottype/${studyId(id)}") =>
      controller.addAnnotationType(id)

    case POST(p"/pannottype/${studyId(id)}/$uniqueId") =>
      controller.updateAnnotationType(id, uniqueId)

    case DELETE(p"/pannottype/${studyId(id)}/${long(ver)}/$uniqueId") =>
      controller.removeAnnotationType(id, ver, uniqueId)

    case POST(p"/enable/${studyId(id)}") =>
      controller.enable(id)

    case POST(p"/disable/${studyId(id)}") =>
      controller.disable(id)

    case POST(p"/retire/${studyId(id)}") =>
      controller.retire(id)

    case POST(p"/unretire/${studyId(id)}") =>
      controller.unretire(id)

    case GET(p"/centres/${studyId(id)}") =>
      controller.centresForStudy(id)

  }
}
