package com.umar.integration.api.grpc;

import com.umar.grpc.hub.v1.*;
import com.umar.integration.service.CatalogService;
import com.umar.integration.service.ExtensionService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.NoSuchElementException;

@Component
@RequiredArgsConstructor
public class ExtensionGrpcServiceImpl extends ExtensionServiceGrpc.ExtensionServiceImplBase {

    private final CatalogService catalogService;
    private final ExtensionService extensionService;

    @Override
    public void registerExtensionPoints(
            RegisterExtensionPointsRequest request,
            StreamObserver<RegisterExtensionPointsResponse> responseObserver) {
        try {
            String callerService = HubGrpcAuthInterceptor.SERVICE_IDENTITY_CTX.get();
            RegisterExtensionPointsResponse resp = catalogService.registerExtensionPoints(callerService, request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (SecurityException e) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    @Override
    public void invokeExtension(
            InvokeExtensionRequest request,
            StreamObserver<InvokeExtensionResponse> responseObserver) {
        try {
            String callerService = HubGrpcAuthInterceptor.SERVICE_IDENTITY_CTX.get();
            InvokeExtensionResponse resp = extensionService.invokeExtension(callerService, request);
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
        } catch (SecurityException e) {
            responseObserver.onError(Status.PERMISSION_DENIED.withDescription(e.getMessage()).asRuntimeException());
        } catch (NoSuchElementException e) {
            responseObserver.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (IllegalStateException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("RESOURCE_EXHAUSTED")) {
                responseObserver.onError(Status.RESOURCE_EXHAUSTED.withDescription(e.getMessage()).asRuntimeException());
            } else {
                responseObserver.onError(Status.FAILED_PRECONDITION.withDescription(e.getMessage()).asRuntimeException());
            }
        } catch (Exception e) {
            responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
