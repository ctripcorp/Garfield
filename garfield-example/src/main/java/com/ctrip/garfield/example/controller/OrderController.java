package com.ctrip.garfield.example.controller;

import com.ctrip.garfield.common.context.GarfieldContext;
import com.ctrip.garfield.common.model.BaseFailureResult;
import com.ctrip.garfield.common.model.BaseResult;
import com.ctrip.garfield.common.model.OperationResult;
import com.ctrip.garfield.example.model.OrderDataUnit;
import com.ctrip.garfield.process.orchestration.ReadOrchestrator;
import com.ctrip.garfield.process.orchestration.WriteOrchestrator;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller demonstrating Garfield read/write operations.
 *
 * @author Trip.com Group
 */
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final WriteOrchestrator writeOrchestrator;
    private final ReadOrchestrator readOrchestrator;

    @PostMapping
    public OrderWriteResponse createOrder(@RequestBody List<OrderRequest> requests) {
        List<OrderDataUnit> dataUnits = new ArrayList<>();
        for (OrderRequest request : requests) {
            OrderDataUnit dataUnit = new OrderDataUnit();
            dataUnit.setOrderId(request.getOrderId());
            dataUnit.setProductName(request.getProductName());
            dataUnit.setAmount(request.getAmount());
            dataUnit.setTimestamp(System.currentTimeMillis());
            dataUnits.add(dataUnit);
        }

        GarfieldContext<OrderDataUnit, BaseFailureResult> context = new GarfieldContext<>();
        context.setReqClassName("OrderDataUnit");
        context.setDataInfos(dataUnits);

        boolean ok = writeOrchestrator.batchPut(context);

        OrderWriteResponse response = new OrderWriteResponse();
        response.setResult(context.getResult());
        if (ok && !context.getErrorDetails().isEmpty()) {
            Map<String, String> failures = new LinkedHashMap<>();
            context.getErrorDetails().forEach((data, failure) ->
                    failures.put(data.getOrderId(), failure.getFailureType()));
            response.setPartialFailures(failures);
        }
        return response;
    }

    @GetMapping("/{orderId}")
    public OperationResult<?> getOrder(@PathVariable String orderId) {
        OrderDataUnit dataUnit = new OrderDataUnit();
        dataUnit.setOrderId(orderId);

        GarfieldContext<OrderDataUnit, BaseFailureResult> context = new GarfieldContext<>();
        context.setReqClassName("OrderDataUnit");
        context.setDataInfos(Collections.singletonList(dataUnit));

        return readOrchestrator.read(context);
    }

    @Data
    static class OrderRequest {
        private String orderId;
        private String productName;
        private long amount;
    }

    @Data
    static class OrderWriteResponse {
        private BaseResult result;
        private Map<String, String> partialFailures;
    }
}
