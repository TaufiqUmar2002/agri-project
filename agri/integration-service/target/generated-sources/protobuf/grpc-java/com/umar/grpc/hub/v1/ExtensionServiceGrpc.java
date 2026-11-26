package com.umar.grpc.hub.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class ExtensionServiceGrpc {

  private ExtensionServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "hub.v1.ExtensionService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.hub.v1.RegisterExtensionPointsRequest,
      com.umar.grpc.hub.v1.RegisterExtensionPointsResponse> getRegisterExtensionPointsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "RegisterExtensionPoints",
      requestType = com.umar.grpc.hub.v1.RegisterExtensionPointsRequest.class,
      responseType = com.umar.grpc.hub.v1.RegisterExtensionPointsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.hub.v1.RegisterExtensionPointsRequest,
      com.umar.grpc.hub.v1.RegisterExtensionPointsResponse> getRegisterExtensionPointsMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.hub.v1.RegisterExtensionPointsRequest, com.umar.grpc.hub.v1.RegisterExtensionPointsResponse> getRegisterExtensionPointsMethod;
    if ((getRegisterExtensionPointsMethod = ExtensionServiceGrpc.getRegisterExtensionPointsMethod) == null) {
      synchronized (ExtensionServiceGrpc.class) {
        if ((getRegisterExtensionPointsMethod = ExtensionServiceGrpc.getRegisterExtensionPointsMethod) == null) {
          ExtensionServiceGrpc.getRegisterExtensionPointsMethod = getRegisterExtensionPointsMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.hub.v1.RegisterExtensionPointsRequest, com.umar.grpc.hub.v1.RegisterExtensionPointsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "RegisterExtensionPoints"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.hub.v1.RegisterExtensionPointsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.hub.v1.RegisterExtensionPointsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ExtensionServiceMethodDescriptorSupplier("RegisterExtensionPoints"))
              .build();
        }
      }
    }
    return getRegisterExtensionPointsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.hub.v1.InvokeExtensionRequest,
      com.umar.grpc.hub.v1.InvokeExtensionResponse> getInvokeExtensionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "InvokeExtension",
      requestType = com.umar.grpc.hub.v1.InvokeExtensionRequest.class,
      responseType = com.umar.grpc.hub.v1.InvokeExtensionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.hub.v1.InvokeExtensionRequest,
      com.umar.grpc.hub.v1.InvokeExtensionResponse> getInvokeExtensionMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.hub.v1.InvokeExtensionRequest, com.umar.grpc.hub.v1.InvokeExtensionResponse> getInvokeExtensionMethod;
    if ((getInvokeExtensionMethod = ExtensionServiceGrpc.getInvokeExtensionMethod) == null) {
      synchronized (ExtensionServiceGrpc.class) {
        if ((getInvokeExtensionMethod = ExtensionServiceGrpc.getInvokeExtensionMethod) == null) {
          ExtensionServiceGrpc.getInvokeExtensionMethod = getInvokeExtensionMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.hub.v1.InvokeExtensionRequest, com.umar.grpc.hub.v1.InvokeExtensionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "InvokeExtension"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.hub.v1.InvokeExtensionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.hub.v1.InvokeExtensionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ExtensionServiceMethodDescriptorSupplier("InvokeExtension"))
              .build();
        }
      }
    }
    return getInvokeExtensionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ExtensionServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceStub>() {
        @java.lang.Override
        public ExtensionServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ExtensionServiceStub(channel, callOptions);
        }
      };
    return ExtensionServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static ExtensionServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceBlockingV2Stub>() {
        @java.lang.Override
        public ExtensionServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ExtensionServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return ExtensionServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ExtensionServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceBlockingStub>() {
        @java.lang.Override
        public ExtensionServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ExtensionServiceBlockingStub(channel, callOptions);
        }
      };
    return ExtensionServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ExtensionServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ExtensionServiceFutureStub>() {
        @java.lang.Override
        public ExtensionServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ExtensionServiceFutureStub(channel, callOptions);
        }
      };
    return ExtensionServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void registerExtensionPoints(com.umar.grpc.hub.v1.RegisterExtensionPointsRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.hub.v1.RegisterExtensionPointsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getRegisterExtensionPointsMethod(), responseObserver);
    }

    /**
     */
    default void invokeExtension(com.umar.grpc.hub.v1.InvokeExtensionRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.hub.v1.InvokeExtensionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getInvokeExtensionMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ExtensionService.
   */
  public static abstract class ExtensionServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ExtensionServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ExtensionService.
   */
  public static final class ExtensionServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ExtensionServiceStub> {
    private ExtensionServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ExtensionServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ExtensionServiceStub(channel, callOptions);
    }

    /**
     */
    public void registerExtensionPoints(com.umar.grpc.hub.v1.RegisterExtensionPointsRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.hub.v1.RegisterExtensionPointsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getRegisterExtensionPointsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void invokeExtension(com.umar.grpc.hub.v1.InvokeExtensionRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.hub.v1.InvokeExtensionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getInvokeExtensionMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ExtensionService.
   */
  public static final class ExtensionServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<ExtensionServiceBlockingV2Stub> {
    private ExtensionServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ExtensionServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ExtensionServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.hub.v1.RegisterExtensionPointsResponse registerExtensionPoints(com.umar.grpc.hub.v1.RegisterExtensionPointsRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getRegisterExtensionPointsMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.hub.v1.InvokeExtensionResponse invokeExtension(com.umar.grpc.hub.v1.InvokeExtensionRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getInvokeExtensionMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service ExtensionService.
   */
  public static final class ExtensionServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ExtensionServiceBlockingStub> {
    private ExtensionServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ExtensionServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ExtensionServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.hub.v1.RegisterExtensionPointsResponse registerExtensionPoints(com.umar.grpc.hub.v1.RegisterExtensionPointsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getRegisterExtensionPointsMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.hub.v1.InvokeExtensionResponse invokeExtension(com.umar.grpc.hub.v1.InvokeExtensionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getInvokeExtensionMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ExtensionService.
   */
  public static final class ExtensionServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ExtensionServiceFutureStub> {
    private ExtensionServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ExtensionServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ExtensionServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.hub.v1.RegisterExtensionPointsResponse> registerExtensionPoints(
        com.umar.grpc.hub.v1.RegisterExtensionPointsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getRegisterExtensionPointsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.hub.v1.InvokeExtensionResponse> invokeExtension(
        com.umar.grpc.hub.v1.InvokeExtensionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getInvokeExtensionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_REGISTER_EXTENSION_POINTS = 0;
  private static final int METHODID_INVOKE_EXTENSION = 1;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final AsyncService serviceImpl;
    private final int methodId;

    MethodHandlers(AsyncService serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_REGISTER_EXTENSION_POINTS:
          serviceImpl.registerExtensionPoints((com.umar.grpc.hub.v1.RegisterExtensionPointsRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.hub.v1.RegisterExtensionPointsResponse>) responseObserver);
          break;
        case METHODID_INVOKE_EXTENSION:
          serviceImpl.invokeExtension((com.umar.grpc.hub.v1.InvokeExtensionRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.hub.v1.InvokeExtensionResponse>) responseObserver);
          break;
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getRegisterExtensionPointsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.hub.v1.RegisterExtensionPointsRequest,
              com.umar.grpc.hub.v1.RegisterExtensionPointsResponse>(
                service, METHODID_REGISTER_EXTENSION_POINTS)))
        .addMethod(
          getInvokeExtensionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.hub.v1.InvokeExtensionRequest,
              com.umar.grpc.hub.v1.InvokeExtensionResponse>(
                service, METHODID_INVOKE_EXTENSION)))
        .build();
  }

  private static abstract class ExtensionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ExtensionServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.grpc.hub.v1.IntegrationHubProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ExtensionService");
    }
  }

  private static final class ExtensionServiceFileDescriptorSupplier
      extends ExtensionServiceBaseDescriptorSupplier {
    ExtensionServiceFileDescriptorSupplier() {}
  }

  private static final class ExtensionServiceMethodDescriptorSupplier
      extends ExtensionServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ExtensionServiceMethodDescriptorSupplier(java.lang.String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (ExtensionServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ExtensionServiceFileDescriptorSupplier())
              .addMethod(getRegisterExtensionPointsMethod())
              .addMethod(getInvokeExtensionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
