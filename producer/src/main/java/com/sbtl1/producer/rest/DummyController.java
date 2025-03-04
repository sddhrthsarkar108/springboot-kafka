package com.sbtl1.producer.rest;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DummyController {

    @Operation(summary = "Echo message", description = "Echo endpoint of module 1")
    @GetMapping("/echo/{msg}")
    public String echo(@PathVariable String msg) {
        return msg;
    }
}
