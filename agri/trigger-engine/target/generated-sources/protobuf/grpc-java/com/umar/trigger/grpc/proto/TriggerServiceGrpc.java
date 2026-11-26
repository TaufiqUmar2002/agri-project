package com.umar.trigger.grpc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class TriggerServiceGrpc {

  private TriggerServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "trigger.v1.TriggerService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.trigger.grpc.proto.GetDecisionStatusRequest,
      com.umar.trigger.grpc.proto.GetDecisionStatusResponse> getGetDecisionStatusMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetDecisionStatus",
      requestType = com.umar.trigger.grpc.proto.GetDecisionStatusRequest.class,
      responseType = com.umar.trigger.grpc.proto.GetDecisionStatusResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.trigger.grpc.proto.GetDecisionStatusRequest,
      com.umar.trigger.grpc.proto.GetDecisionStatusResponse> getGetDecisionStatusMethod() {
    io.grpc.MethodDescriptor<com.umar.trigger.grpc.proto.GetDecisionStatusRequest, com.umar.trigger.grpc.proto.GetDecisionStatusResponse> getGetDecisionStatusMethod;
    if ((getGetDecisionStatusMethod = TriggerServiceGrpc.getGetDecisionStatusMethod) == null) {
      synchronized (TriggerServiceGrpc.class) {
        if ((getGetDecisionStatusMethod = TriggerServiceGrpc.getGetDecisionStatusMethod) == null) {
          TriggerServiceGrpc.getGetDecisionStatusMethod = getGetDecisionStatusMethod =
              io.grpc.MethodDescriptor.<com.umar.trigger.grpc.proto.GetDecisionStatusRequest, com.umar.trigger.grpc.proto.GetDecisionStatusResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetDecisionStatus"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.trigger.grpc.proto.GetDecisionStatusRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.trigger.grpc.proto.GetDecisionStatusResponse.getDefaultInstance()))
              .setSchemaDescriptor(new TriggerServiceMethodDescriptorSupplier("GetDecisionStatus"))
              .build();
        }
      }
    }
    return getGetDecisionStatusMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.trigger.grpc.proto.GetSimulationSummaryRequest,
      com.umar.trigger.grpc.proto.GetSimulationSummaryResponse> getGetSimulationSummaryMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetSimulationSummary",
      requestType = com.umar.trigger.grpc.proto.GetSimulationSummaryRequest.class,
      responseType = com.umar.trigger.grpc.proto.GetSimulationSummaryResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.trigger.grpc.proto.GetSimulationSummaryRequest,
      com.umar.trigger.grpc.proto.GetSimulationSummaryResponse> getGetSimulationSummaryMethod() {
    io.grpc.MethodDescriptor<com.umar.trigger.grpc.proto.GetSimulationSummaryRequest, com.umar.trigger.grpc.proto.GetSimulationSummaryResponse> getGetSimulationSummaryMethod;
    if ((getGetSimulationSummaryMethod = TriggerServiceGrpc.getGetSimulationSummaryMethod) == null) {
      synchronized (TriggerServiceGrpc.class) {
        if ((getGetSimulationSummaryMethod = TriggerServiceGrpc.getGetSimulationSummaryMethod) == null) {
          TriggerServiceGrpc.getGetSimulationSummaryMethod = getGetSimulationSummaryMethod =
              io.grpc.MethodDescriptor.<com.umar.trigger.grpc.proto.GetSimulationSummaryRequest, com.umar.trigger.grpc.proto.GetSimulationSummaryResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetSimulationSummary"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.trigger.grpc.proto.GetSimulationSummaryRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.trigger.grpc.proto.GetSimulationSummaryResponse.getDefaultInstance()))
              .setSchemaDescriptor(new TriggerServiceMethodDescriptorSupplier("GetSimulationSummary"))
              .build();
        }
      }
    }
    return getGetSimulationSummaryMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static TriggerServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TriggerServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TriggerServiceStub>() {
        @java.lang.Override
        public TriggerServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TriggerServiceStub(channel, callOptions);
        }
      };
    return TriggerServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static TriggerServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TriggerServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TriggerServiceBlockingV2Stub>() {
        @java.lang.Override
        public TriggerServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TriggerServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return TriggerServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static TriggerServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TriggerServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TriggerServiceBlockingStub>() {
        @java.lang.Override
        public TriggerServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TriggerServiceBlockingStub(channel, callOptions);
        }
      };
    return TriggerServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static TriggerServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<TriggerServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<TriggerServiceFutureStub>() {
        @java.lang.Override
        public TriggerServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new TriggerServiceFutureStub(channel, callOptions);
        }
      };
    return TriggerServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void getDecisionStatus(com.umar.trigger.grpc.proto.GetDecisionStatusRequest request,
        io.grpc.stub.StreamObserver<com.umar.trigger.grpc.proto.GetDecisionStatusResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetDecisionStatusMethod(), responseObserver);
    }

    /**
     */
    default void getSimulationSummary(com.umar.trigger.grpc.proto.GetSimulationSummaryRequest request,
        io.grpc.stub.StreamObserver<com.umar.trigger.grpc.proto.GetSimulationSummaryResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetSimulationSummaryMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service TriggerService.
   */
  public static abstract class TriggerServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return TriggerServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service TriggerService.
   */
  public static final class TriggerServiceStub
      extends io.grpc.stub.AbstractAsyncStub<TriggerServiceStub> {
    private TriggerServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TriggerServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TriggerServiceStub(channel, callOptions);
    }

    /**
     */
    public void getDecisionStatus(com.umar.trigger.grpc.proto.GetDecisionStatusRequest request,
        io.grpc.stub.StreamObserver<com.umar.trigger.grpc.proto.GetDecisionStatusResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetDecisionStatusMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getSimulationSummary(com.umar.trigger.grpc.proto.GetSimulationSummaryRequest request,
        io.grpc.stub.StreamObserver<com.umar.trigger.grpc.proto.GetSimulationSummaryResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetSimulationSummaryMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service TriggerService.
   */
  public static final class TriggerServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<TriggerServiceBlockingV2Stub> {
    private TriggerServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TriggerServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TriggerServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.trigger.grpc.proto.GetDecisionStatusResponse getDecisionStatus(com.umar.trigger.grpc.proto.GetDecisionStatusRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetDecisionStatusMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.trigger.grpc.proto.GetSimulationSummaryResponse getSimulationSummary(com.umar.trigger.grpc.proto.GetSimulationSummaryRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetSimulationSummaryMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service TriggerService.
   */
  public static final class TriggerServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<TriggerServiceBlockingStub> {
    private TriggerServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TriggerServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TriggerServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.trigger.grpc.proto.GetDecisionStatusResponse getDecisionStatus(com.umar.trigger.grpc.proto.GetDecisionStatusRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetDecisionStatusMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.trigger.grpc.proto.GetSimulationSummaryResponse getSimulationSummary(com.umar.trigger.grpc.proto.GetSimulationSummaryRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetSimulationSummaryMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service TriggerService.
   */
  public static final class TriggerServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<TriggerServiceFutureStub> {
    private TriggerServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected TriggerServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new TriggerServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.trigger.grpc.proto.GetDecisionStatusResponse> getDecisionStatus(
        com.umar.trigger.grpc.proto.GetDecisionStatusRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetDecisionStatusMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.trigger.grpc.proto.GetSimulationSummaryResponse> getSimulationSummary(
        com.umar.trigger.grpc.proto.GetSimulationSummaryRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetSimulationSummaryMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_DECISION_STATUS = 0;
  private static final int METHODID_GET_SIMULATION_SUMMARY = 1;

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
        case METHODID_GET_DECISION_STATUS:
          serviceImpl.getDecisionStatus((com.umar.trigger.grpc.proto.GetDecisionStatusRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.trigger.grpc.proto.GetDecisionStatusResponse>) responseObserver);
          break;
        case METHODID_GET_SIMULATION_SUMMARY:
          serviceImpl.getSimulationSummary((com.umar.trigger.grpc.proto.GetSimulationSummaryRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.trigger.grpc.proto.GetSimulationSummaryResponse>) responseObserver);
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
          getGetDecisionStatusMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.trigger.grpc.proto.GetDecisionStatusRequest,
              com.umar.trigger.grpc.proto.GetDecisionStatusResponse>(
                service, METHODID_GET_DECISION_STATUS)))
        .addMethod(
          getGetSimulationSummaryMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.trigger.grpc.proto.GetSimulationSummaryRequest,
              com.umar.trigger.grpc.proto.GetSimulationSummaryResponse>(
                service, METHODID_GET_SIMULATION_SUMMARY)))
        .build();
  }

  private static abstract class TriggerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    TriggerServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.trigger.grpc.proto.TriggerProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("TriggerService");
    }
  }

  private static final class TriggerServiceFileDescriptorSupplier
      extends TriggerServiceBaseDescriptorSupplier {
    TriggerServiceFileDescriptorSupplier() {}
  }

  private static final class TriggerServiceMethodDescriptorSupplier
      extends TriggerServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    TriggerServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (TriggerServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new TriggerServiceFileDescriptorSupplier())
              .addMethod(getGetDecisionStatusMethod())
              .addMethod(getGetSimulationSummaryMethod())
              .build();
        }
      }
    }
    return result;
  }
}
