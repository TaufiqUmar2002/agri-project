package com.umar.riskpool.grpc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class PoolServiceGrpc {

  private PoolServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "pool.v1.PoolService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.riskpool.grpc.proto.CheckCapacityRequest,
      com.umar.riskpool.grpc.proto.CheckCapacityResponse> getCheckCapacityMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "CheckCapacity",
      requestType = com.umar.riskpool.grpc.proto.CheckCapacityRequest.class,
      responseType = com.umar.riskpool.grpc.proto.CheckCapacityResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.riskpool.grpc.proto.CheckCapacityRequest,
      com.umar.riskpool.grpc.proto.CheckCapacityResponse> getCheckCapacityMethod() {
    io.grpc.MethodDescriptor<com.umar.riskpool.grpc.proto.CheckCapacityRequest, com.umar.riskpool.grpc.proto.CheckCapacityResponse> getCheckCapacityMethod;
    if ((getCheckCapacityMethod = PoolServiceGrpc.getCheckCapacityMethod) == null) {
      synchronized (PoolServiceGrpc.class) {
        if ((getCheckCapacityMethod = PoolServiceGrpc.getCheckCapacityMethod) == null) {
          PoolServiceGrpc.getCheckCapacityMethod = getCheckCapacityMethod =
              io.grpc.MethodDescriptor.<com.umar.riskpool.grpc.proto.CheckCapacityRequest, com.umar.riskpool.grpc.proto.CheckCapacityResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "CheckCapacity"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.riskpool.grpc.proto.CheckCapacityRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.riskpool.grpc.proto.CheckCapacityResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PoolServiceMethodDescriptorSupplier("CheckCapacity"))
              .build();
        }
      }
    }
    return getCheckCapacityMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PoolServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PoolServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PoolServiceStub>() {
        @java.lang.Override
        public PoolServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PoolServiceStub(channel, callOptions);
        }
      };
    return PoolServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static PoolServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PoolServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PoolServiceBlockingV2Stub>() {
        @java.lang.Override
        public PoolServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PoolServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return PoolServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PoolServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PoolServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PoolServiceBlockingStub>() {
        @java.lang.Override
        public PoolServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PoolServiceBlockingStub(channel, callOptions);
        }
      };
    return PoolServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PoolServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PoolServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PoolServiceFutureStub>() {
        @java.lang.Override
        public PoolServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PoolServiceFutureStub(channel, callOptions);
        }
      };
    return PoolServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void checkCapacity(com.umar.riskpool.grpc.proto.CheckCapacityRequest request,
        io.grpc.stub.StreamObserver<com.umar.riskpool.grpc.proto.CheckCapacityResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getCheckCapacityMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PoolService.
   */
  public static abstract class PoolServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PoolServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PoolService.
   */
  public static final class PoolServiceStub
      extends io.grpc.stub.AbstractAsyncStub<PoolServiceStub> {
    private PoolServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PoolServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PoolServiceStub(channel, callOptions);
    }

    /**
     */
    public void checkCapacity(com.umar.riskpool.grpc.proto.CheckCapacityRequest request,
        io.grpc.stub.StreamObserver<com.umar.riskpool.grpc.proto.CheckCapacityResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getCheckCapacityMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PoolService.
   */
  public static final class PoolServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<PoolServiceBlockingV2Stub> {
    private PoolServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PoolServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PoolServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.riskpool.grpc.proto.CheckCapacityResponse checkCapacity(com.umar.riskpool.grpc.proto.CheckCapacityRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getCheckCapacityMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service PoolService.
   */
  public static final class PoolServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PoolServiceBlockingStub> {
    private PoolServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PoolServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PoolServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.riskpool.grpc.proto.CheckCapacityResponse checkCapacity(com.umar.riskpool.grpc.proto.CheckCapacityRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getCheckCapacityMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PoolService.
   */
  public static final class PoolServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<PoolServiceFutureStub> {
    private PoolServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PoolServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PoolServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.riskpool.grpc.proto.CheckCapacityResponse> checkCapacity(
        com.umar.riskpool.grpc.proto.CheckCapacityRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getCheckCapacityMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_CHECK_CAPACITY = 0;

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
        case METHODID_CHECK_CAPACITY:
          serviceImpl.checkCapacity((com.umar.riskpool.grpc.proto.CheckCapacityRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.riskpool.grpc.proto.CheckCapacityResponse>) responseObserver);
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
          getCheckCapacityMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.riskpool.grpc.proto.CheckCapacityRequest,
              com.umar.riskpool.grpc.proto.CheckCapacityResponse>(
                service, METHODID_CHECK_CAPACITY)))
        .build();
  }

  private static abstract class PoolServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PoolServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.riskpool.grpc.proto.PoolProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PoolService");
    }
  }

  private static final class PoolServiceFileDescriptorSupplier
      extends PoolServiceBaseDescriptorSupplier {
    PoolServiceFileDescriptorSupplier() {}
  }

  private static final class PoolServiceMethodDescriptorSupplier
      extends PoolServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PoolServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (PoolServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PoolServiceFileDescriptorSupplier())
              .addMethod(getCheckCapacityMethod())
              .build();
        }
      }
    }
    return result;
  }
}
