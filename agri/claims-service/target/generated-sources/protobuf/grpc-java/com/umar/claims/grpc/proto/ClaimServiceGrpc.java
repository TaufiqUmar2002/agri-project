package com.umar.claims.grpc.proto;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class ClaimServiceGrpc {

  private ClaimServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "claim.v1.ClaimService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.claims.grpc.proto.GetPaymentInstructionRequest,
      com.umar.claims.grpc.proto.GetPaymentInstructionResponse> getGetPaymentInstructionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPaymentInstruction",
      requestType = com.umar.claims.grpc.proto.GetPaymentInstructionRequest.class,
      responseType = com.umar.claims.grpc.proto.GetPaymentInstructionResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.claims.grpc.proto.GetPaymentInstructionRequest,
      com.umar.claims.grpc.proto.GetPaymentInstructionResponse> getGetPaymentInstructionMethod() {
    io.grpc.MethodDescriptor<com.umar.claims.grpc.proto.GetPaymentInstructionRequest, com.umar.claims.grpc.proto.GetPaymentInstructionResponse> getGetPaymentInstructionMethod;
    if ((getGetPaymentInstructionMethod = ClaimServiceGrpc.getGetPaymentInstructionMethod) == null) {
      synchronized (ClaimServiceGrpc.class) {
        if ((getGetPaymentInstructionMethod = ClaimServiceGrpc.getGetPaymentInstructionMethod) == null) {
          ClaimServiceGrpc.getGetPaymentInstructionMethod = getGetPaymentInstructionMethod =
              io.grpc.MethodDescriptor.<com.umar.claims.grpc.proto.GetPaymentInstructionRequest, com.umar.claims.grpc.proto.GetPaymentInstructionResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPaymentInstruction"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.claims.grpc.proto.GetPaymentInstructionRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.claims.grpc.proto.GetPaymentInstructionResponse.getDefaultInstance()))
              .setSchemaDescriptor(new ClaimServiceMethodDescriptorSupplier("GetPaymentInstruction"))
              .build();
        }
      }
    }
    return getGetPaymentInstructionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static ClaimServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ClaimServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ClaimServiceStub>() {
        @java.lang.Override
        public ClaimServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ClaimServiceStub(channel, callOptions);
        }
      };
    return ClaimServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static ClaimServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ClaimServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ClaimServiceBlockingV2Stub>() {
        @java.lang.Override
        public ClaimServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ClaimServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return ClaimServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static ClaimServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ClaimServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ClaimServiceBlockingStub>() {
        @java.lang.Override
        public ClaimServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ClaimServiceBlockingStub(channel, callOptions);
        }
      };
    return ClaimServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static ClaimServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<ClaimServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<ClaimServiceFutureStub>() {
        @java.lang.Override
        public ClaimServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new ClaimServiceFutureStub(channel, callOptions);
        }
      };
    return ClaimServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void getPaymentInstruction(com.umar.claims.grpc.proto.GetPaymentInstructionRequest request,
        io.grpc.stub.StreamObserver<com.umar.claims.grpc.proto.GetPaymentInstructionResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPaymentInstructionMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service ClaimService.
   */
  public static abstract class ClaimServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return ClaimServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service ClaimService.
   */
  public static final class ClaimServiceStub
      extends io.grpc.stub.AbstractAsyncStub<ClaimServiceStub> {
    private ClaimServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ClaimServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ClaimServiceStub(channel, callOptions);
    }

    /**
     */
    public void getPaymentInstruction(com.umar.claims.grpc.proto.GetPaymentInstructionRequest request,
        io.grpc.stub.StreamObserver<com.umar.claims.grpc.proto.GetPaymentInstructionResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPaymentInstructionMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service ClaimService.
   */
  public static final class ClaimServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<ClaimServiceBlockingV2Stub> {
    private ClaimServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ClaimServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ClaimServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.claims.grpc.proto.GetPaymentInstructionResponse getPaymentInstruction(com.umar.claims.grpc.proto.GetPaymentInstructionRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetPaymentInstructionMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service ClaimService.
   */
  public static final class ClaimServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<ClaimServiceBlockingStub> {
    private ClaimServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ClaimServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ClaimServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.claims.grpc.proto.GetPaymentInstructionResponse getPaymentInstruction(com.umar.claims.grpc.proto.GetPaymentInstructionRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPaymentInstructionMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service ClaimService.
   */
  public static final class ClaimServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<ClaimServiceFutureStub> {
    private ClaimServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected ClaimServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new ClaimServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.claims.grpc.proto.GetPaymentInstructionResponse> getPaymentInstruction(
        com.umar.claims.grpc.proto.GetPaymentInstructionRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPaymentInstructionMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_PAYMENT_INSTRUCTION = 0;

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
        case METHODID_GET_PAYMENT_INSTRUCTION:
          serviceImpl.getPaymentInstruction((com.umar.claims.grpc.proto.GetPaymentInstructionRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.claims.grpc.proto.GetPaymentInstructionResponse>) responseObserver);
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
          getGetPaymentInstructionMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.claims.grpc.proto.GetPaymentInstructionRequest,
              com.umar.claims.grpc.proto.GetPaymentInstructionResponse>(
                service, METHODID_GET_PAYMENT_INSTRUCTION)))
        .build();
  }

  private static abstract class ClaimServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    ClaimServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.claims.grpc.proto.ClaimProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("ClaimService");
    }
  }

  private static final class ClaimServiceFileDescriptorSupplier
      extends ClaimServiceBaseDescriptorSupplier {
    ClaimServiceFileDescriptorSupplier() {}
  }

  private static final class ClaimServiceMethodDescriptorSupplier
      extends ClaimServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    ClaimServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (ClaimServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new ClaimServiceFileDescriptorSupplier())
              .addMethod(getGetPaymentInstructionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
