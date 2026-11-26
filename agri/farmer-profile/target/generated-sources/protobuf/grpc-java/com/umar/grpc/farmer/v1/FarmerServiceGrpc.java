package com.umar.grpc.farmer.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class FarmerServiceGrpc {

  private FarmerServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "farmer.v1.FarmerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.farmer.v1.GetPayoutDestinationRequest,
      com.umar.grpc.farmer.v1.GetPayoutDestinationResponse> getGetPayoutDestinationMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPayoutDestination",
      requestType = com.umar.grpc.farmer.v1.GetPayoutDestinationRequest.class,
      responseType = com.umar.grpc.farmer.v1.GetPayoutDestinationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.farmer.v1.GetPayoutDestinationRequest,
      com.umar.grpc.farmer.v1.GetPayoutDestinationResponse> getGetPayoutDestinationMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.farmer.v1.GetPayoutDestinationRequest, com.umar.grpc.farmer.v1.GetPayoutDestinationResponse> getGetPayoutDestinationMethod;
    if ((getGetPayoutDestinationMethod = FarmerServiceGrpc.getGetPayoutDestinationMethod) == null) {
      synchronized (FarmerServiceGrpc.class) {
        if ((getGetPayoutDestinationMethod = FarmerServiceGrpc.getGetPayoutDestinationMethod) == null) {
          FarmerServiceGrpc.getGetPayoutDestinationMethod = getGetPayoutDestinationMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.farmer.v1.GetPayoutDestinationRequest, com.umar.grpc.farmer.v1.GetPayoutDestinationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPayoutDestination"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.farmer.v1.GetPayoutDestinationRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.farmer.v1.GetPayoutDestinationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new FarmerServiceMethodDescriptorSupplier("GetPayoutDestination"))
              .build();
        }
      }
    }
    return getGetPayoutDestinationMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.farmer.v1.GetFarmerContextRequest,
      com.umar.grpc.farmer.v1.GetFarmerContextResponse> getGetFarmerContextMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetFarmerContext",
      requestType = com.umar.grpc.farmer.v1.GetFarmerContextRequest.class,
      responseType = com.umar.grpc.farmer.v1.GetFarmerContextResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.farmer.v1.GetFarmerContextRequest,
      com.umar.grpc.farmer.v1.GetFarmerContextResponse> getGetFarmerContextMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.farmer.v1.GetFarmerContextRequest, com.umar.grpc.farmer.v1.GetFarmerContextResponse> getGetFarmerContextMethod;
    if ((getGetFarmerContextMethod = FarmerServiceGrpc.getGetFarmerContextMethod) == null) {
      synchronized (FarmerServiceGrpc.class) {
        if ((getGetFarmerContextMethod = FarmerServiceGrpc.getGetFarmerContextMethod) == null) {
          FarmerServiceGrpc.getGetFarmerContextMethod = getGetFarmerContextMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.farmer.v1.GetFarmerContextRequest, com.umar.grpc.farmer.v1.GetFarmerContextResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetFarmerContext"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.farmer.v1.GetFarmerContextRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.farmer.v1.GetFarmerContextResponse.getDefaultInstance()))
              .setSchemaDescriptor(new FarmerServiceMethodDescriptorSupplier("GetFarmerContext"))
              .build();
        }
      }
    }
    return getGetFarmerContextMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static FarmerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FarmerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FarmerServiceStub>() {
        @java.lang.Override
        public FarmerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FarmerServiceStub(channel, callOptions);
        }
      };
    return FarmerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static FarmerServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FarmerServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FarmerServiceBlockingV2Stub>() {
        @java.lang.Override
        public FarmerServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FarmerServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return FarmerServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static FarmerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FarmerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FarmerServiceBlockingStub>() {
        @java.lang.Override
        public FarmerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FarmerServiceBlockingStub(channel, callOptions);
        }
      };
    return FarmerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static FarmerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<FarmerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<FarmerServiceFutureStub>() {
        @java.lang.Override
        public FarmerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new FarmerServiceFutureStub(channel, callOptions);
        }
      };
    return FarmerServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void getPayoutDestination(com.umar.grpc.farmer.v1.GetPayoutDestinationRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.farmer.v1.GetPayoutDestinationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPayoutDestinationMethod(), responseObserver);
    }

    /**
     */
    default void getFarmerContext(com.umar.grpc.farmer.v1.GetFarmerContextRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.farmer.v1.GetFarmerContextResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetFarmerContextMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service FarmerService.
   */
  public static abstract class FarmerServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return FarmerServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service FarmerService.
   */
  public static final class FarmerServiceStub
      extends io.grpc.stub.AbstractAsyncStub<FarmerServiceStub> {
    private FarmerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FarmerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FarmerServiceStub(channel, callOptions);
    }

    /**
     */
    public void getPayoutDestination(com.umar.grpc.farmer.v1.GetPayoutDestinationRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.farmer.v1.GetPayoutDestinationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPayoutDestinationMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getFarmerContext(com.umar.grpc.farmer.v1.GetFarmerContextRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.farmer.v1.GetFarmerContextResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetFarmerContextMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service FarmerService.
   */
  public static final class FarmerServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<FarmerServiceBlockingV2Stub> {
    private FarmerServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FarmerServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FarmerServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.farmer.v1.GetPayoutDestinationResponse getPayoutDestination(com.umar.grpc.farmer.v1.GetPayoutDestinationRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetPayoutDestinationMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.farmer.v1.GetFarmerContextResponse getFarmerContext(com.umar.grpc.farmer.v1.GetFarmerContextRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetFarmerContextMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service FarmerService.
   */
  public static final class FarmerServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<FarmerServiceBlockingStub> {
    private FarmerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FarmerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FarmerServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.farmer.v1.GetPayoutDestinationResponse getPayoutDestination(com.umar.grpc.farmer.v1.GetPayoutDestinationRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPayoutDestinationMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.farmer.v1.GetFarmerContextResponse getFarmerContext(com.umar.grpc.farmer.v1.GetFarmerContextRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetFarmerContextMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service FarmerService.
   */
  public static final class FarmerServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<FarmerServiceFutureStub> {
    private FarmerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected FarmerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new FarmerServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.farmer.v1.GetPayoutDestinationResponse> getPayoutDestination(
        com.umar.grpc.farmer.v1.GetPayoutDestinationRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPayoutDestinationMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.farmer.v1.GetFarmerContextResponse> getFarmerContext(
        com.umar.grpc.farmer.v1.GetFarmerContextRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetFarmerContextMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_PAYOUT_DESTINATION = 0;
  private static final int METHODID_GET_FARMER_CONTEXT = 1;

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
        case METHODID_GET_PAYOUT_DESTINATION:
          serviceImpl.getPayoutDestination((com.umar.grpc.farmer.v1.GetPayoutDestinationRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.farmer.v1.GetPayoutDestinationResponse>) responseObserver);
          break;
        case METHODID_GET_FARMER_CONTEXT:
          serviceImpl.getFarmerContext((com.umar.grpc.farmer.v1.GetFarmerContextRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.farmer.v1.GetFarmerContextResponse>) responseObserver);
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
          getGetPayoutDestinationMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.farmer.v1.GetPayoutDestinationRequest,
              com.umar.grpc.farmer.v1.GetPayoutDestinationResponse>(
                service, METHODID_GET_PAYOUT_DESTINATION)))
        .addMethod(
          getGetFarmerContextMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.farmer.v1.GetFarmerContextRequest,
              com.umar.grpc.farmer.v1.GetFarmerContextResponse>(
                service, METHODID_GET_FARMER_CONTEXT)))
        .build();
  }

  private static abstract class FarmerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    FarmerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.grpc.farmer.v1.FarmerProfileProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("FarmerService");
    }
  }

  private static final class FarmerServiceFileDescriptorSupplier
      extends FarmerServiceBaseDescriptorSupplier {
    FarmerServiceFileDescriptorSupplier() {}
  }

  private static final class FarmerServiceMethodDescriptorSupplier
      extends FarmerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    FarmerServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (FarmerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new FarmerServiceFileDescriptorSupplier())
              .addMethod(getGetPayoutDestinationMethod())
              .addMethod(getGetFarmerContextMethod())
              .build();
        }
      }
    }
    return result;
  }
}
