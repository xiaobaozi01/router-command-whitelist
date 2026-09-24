package com.example.whitelist.service.ai;

public interface AiGatewayClient {
    String protocol();

    String generate(AiGatewayRequest request);
}
