package com.drestaurant.query.handler

import com.drestaurant.courier.domain.api.CourierOrderAssignedEvent
import com.drestaurant.courier.domain.api.CourierOrderCreatedEvent
import com.drestaurant.courier.domain.api.CourierOrderDeliveredEvent
import com.drestaurant.courier.domain.api.CourierOrderNotAssignedEvent
import com.drestaurant.courier.domain.api.model.CourierOrderState
import com.drestaurant.courier.query.api.FindCourierOrderQuery
import com.drestaurant.courier.query.api.model.CourierModel
import com.drestaurant.courier.query.api.model.CourierOrderModel
import com.drestaurant.query.model.CourierEntity
import com.drestaurant.query.model.CourierOrderEntity
import com.drestaurant.query.repository.CourierOrderRepository
import com.drestaurant.query.repository.CourierRepository
import org.axonframework.config.ProcessingGroup
import org.axonframework.eventhandling.AllowReplay
import org.axonframework.eventhandling.EventHandler
import org.axonframework.eventhandling.ResetHandler
import org.axonframework.eventhandling.SequenceNumber
import org.axonframework.queryhandling.QueryHandler
import org.axonframework.queryhandling.QueryUpdateEmitter
import org.springframework.stereotype.Component

@Component
@ProcessingGroup("courierorder")
internal class CourierOrderHandler(private val repository: CourierOrderRepository, private val courierRepository: CourierRepository, private val queryUpdateEmitter: QueryUpdateEmitter) {

    private fun convert(record: CourierEntity): CourierModel = CourierModel(record.id, record.aggregateVersion, record.firstName, record.lastName, record.maxNumberOfActiveOrders, emptyList())
    private fun convert(record: CourierOrderEntity): CourierOrderModel = CourierOrderModel(record.id, record.aggregateVersion, convert(record.courier!!), record.state)

    @EventHandler
    /* It is possible to allow or prevent some handlers from being replayed/reset */
    @AllowReplay(true)
    fun handle(event: CourierOrderCreatedEvent, @SequenceNumber aggregateVersion: Long) {
        val record = CourierOrderEntity(event.aggregateIdentifier.identifier, aggregateVersion, null, CourierOrderState.CREATED)
        repository.save(record)
    }

    @EventHandler
    /* It is possible to allow or prevent some handlers from being replayed/reset */
    @AllowReplay(true)
    fun handle(event: CourierOrderAssignedEvent, @SequenceNumber aggregateVersion: Long) {
        val courierEntity = courierRepository.findById(event.courierId.identifier).orElseThrow { UnsupportedOperationException("Courier with id '" + event.courierId + "' not found") }
        val record = repository.findById(event.aggregateIdentifier.identifier).orElseThrow { UnsupportedOperationException("Courier order with id '" + event.aggregateIdentifier + "' not found") }
        record.state = CourierOrderState.ASSIGNED
        record.courier = courierEntity
        repository.save(record)

        /* sending it to subscription queries of type FindCourierOrderQuery, but only if the courier order id matches. */
        queryUpdateEmitter.emit(
                FindCourierOrderQuery::class.java,
                { query -> query.courierOrderId == event.aggregateIdentifier },
                convert(record)
        )
    }

    @EventHandler
    /* It is possible to allow or prevent some handlers from being replayed/reset */
    @AllowReplay(true)
    fun handle(event: CourierOrderNotAssignedEvent, @SequenceNumber aggregateVersion: Long) {
        val record = repository.findById(event.aggregateIdentifier.identifier).orElseThrow { UnsupportedOperationException("Courier order with id '" + event.aggregateIdentifier + "' not found") }

        /* sending it to subscription queries of type FindCourierOrderQuery, but only if the courier order id matches. */
        queryUpdateEmitter.emit(
                FindCourierOrderQuery::class.java,
                { query -> query.courierOrderId == event.aggregateIdentifier },
                convert(record)
        )
    }

    @EventHandler
    /* It is possible to allow or prevent some handlers from being replayed/reset */
    @AllowReplay(true)
    fun handle(event: CourierOrderDeliveredEvent, @SequenceNumber aggregateVersion: Long) {
        val record = repository.findById(event.aggregateIdentifier.identifier).orElseThrow { UnsupportedOperationException("Courier order with id '" + event.aggregateIdentifier + "' not found") }
        record.state = CourierOrderState.DELIVERED
        repository.save(record)

        /* sending it to subscription queries of type FindCourierOrderQuery, but only if the courier order id matches. */
        queryUpdateEmitter.emit(
                FindCourierOrderQuery::class.java,
                { query -> query.courierOrderId == event.aggregateIdentifier },
                convert(record)
        )
    }

    /* Will be called before replay/reset starts. Do pre-reset logic, like clearing out the Projection table */
    @ResetHandler
    fun onReset() = repository.deleteAll()

    @QueryHandler
    fun handle(query: FindCourierOrderQuery): CourierOrderModel = convert(repository.findById(query.courierOrderId.identifier).orElseThrow { UnsupportedOperationException("Courier order with id '" + query.courierOrderId + "' not found") })
}
