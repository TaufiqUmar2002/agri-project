package com.umar.grpc.auth.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class IdentityServiceGrpc {

  private IdentityServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "auth.v1.IdentityService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.auth.v1.GetIdentityContextRequest,
      com.umar.grpc.auth.v1.GetIdentityContextResponse> getGetIdentityContextMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetIdentityContext",
      requestType = com.umar.grpc.auth.v1.GetIdentityContextRequest.class,
      responseType = com.umar.grpc.auth.v1.GetIdentityContextResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.auth.v1.GetIdentityContextRequest,
      com.umar.grpc.auth.v1.GetIdentityContextResponse> getGetIdentityContextMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.auth.v1.GetIdentityContextRequest, com.umar.grpc.auth.v1.GetIdentityContextResponse> getGetIdentityContextMethod;
    if ((getGetIdentityContextMethod = IdentityServiceGrpc.getGetIdentityContextMethod) == null) {
      synchronized (IdentityServiceGrpc.class) {
        if ((getGetIdentityContextMethod = IdentityServiceGrpc.getGetIdentityContextMethod) == null) {
          IdentityServiceGrpc.getGetIdentityContextMethod = getGetIdentityContextMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.auth.v1.GetIdentityContextRequest, com.umar.grpc.auth.v1.GetIdentityContextResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetIdentityContext"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.auth.v1.GetIdentityContextRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.auth.v1.GetIdentityContextResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityServiceMethodDescriptorSupplier("GetIdentityContext"))
              .build();
        }
      }
    }
    return getGetIdentityContextMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.auth.v1.ValidateDelegationRequest,
      com.umar.grpc.auth.v1.ValidateDelegationResponse> getValidateDelegationMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ValidateDelegation",
      requestType = com.umar.grpc.auth.v1.ValidateDelegationRequest.class,
      responseType = com.umar.grpc.auth.v1.ValidateDelegationResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.auth.v1.ValidateDelegationRequest,
      com.umar.grpc.auth.v1.ValidateDelegationResponse> getValidateDelegationMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.auth.v1.ValidateDelegationRequest, com.umar.grpc.auth.v1.ValidateDelegationResponse> getValidateDelegationMethod;
    if ((getValidateDelegationMethod = IdentityServiceGrpc.getValidateDelegationMethod) == null) {
      synchronized (IdentityServiceGrpc.class) {
        if ((getValidateDelegationMethod = IdentityServiceGrpc.getValidateDelegationMethod) == null) {
          IdentityServiceGrpc.getValidateDelegationMethod = getValidateDelegationMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.auth.v1.ValidateDelegationRequest, com.umar.grpc.auth.v1.ValidateDelegationResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ValidateDelegation"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.auth.v1.ValidateDelegationRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.auth.v1.ValidateDelegationResponse.getDefaultInstance()))
              .setSchemaDescriptor(new IdentityServiceMethodDescriptorSupplier("ValidateDelegation"))
              .build();
        }
      }
    }
    return getValidateDelegationMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static IdentityServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityServiceStub>() {
        @java.lang.Override
        public IdentityServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityServiceStub(channel, callOptions);
        }
      };
    return IdentityServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static IdentityServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityServiceBlockingV2Stub>() {
        @java.lang.Override
        public IdentityServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return IdentityServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static IdentityServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityServiceBlockingStub>() {
        @java.lang.Override
        public IdentityServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityServiceBlockingStub(channel, callOptions);
        }
      };
    return IdentityServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static IdentityServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<IdentityServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<IdentityServiceFutureStub>() {
        @java.lang.Override
        public IdentityServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new IdentityServiceFutureStub(channel, callOptions);
        }
      };
    return IdentityServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void getIdentityContext(com.umar.grpc.auth.v1.GetIdentityContextRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.auth.v1.GetIdentityContextResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetIdentityContextMethod(), responseObserver);
    }

    /**
     */
    default void validateDelegation(com.umar.grpc.auth.v1.ValidateDelegationRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.auth.v1.ValidateDelegationResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getValidateDelegationMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service IdentityService.
   */
  public static abstract class IdentityServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return IdentityServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service IdentityService.
   */
  public static final class IdentityServiceStub
      extends io.grpc.stub.AbstractAsyncStub<IdentityServiceStub> {
    private IdentityServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityServiceStub(channel, callOptions);
    }

    /**
     */
    public void getIdentityContext(com.umar.grpc.auth.v1.GetIdentityContextRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.auth.v1.GetIdentityContextResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetIdentityContextMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void validateDelegation(com.umar.grpc.auth.v1.ValidateDelegationRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.auth.v1.ValidateDelegationResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getValidateDelegationMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service IdentityService.
   */
  public static final class IdentityServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<IdentityServiceBlockingV2Stub> {
    private IdentityServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.auth.v1.GetIdentityContextResponse getIdentityContext(com.umar.grpc.auth.v1.GetIdentityContextRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetIdentityContextMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.auth.v1.ValidateDelegationResponse validateDelegation(com.umar.grpc.auth.v1.ValidateDelegationRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getValidateDelegationMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service IdentityService.
   */
  public static final class IdentityServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<IdentityServiceBlockingStub> {
    private IdentityServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.auth.v1.GetIdentityContextResponse getIdentityContext(com.umar.grpc.auth.v1.GetIdentityContextRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetIdentityContextMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.auth.v1.ValidateDelegationResponse validateDelegation(com.umar.grpc.auth.v1.ValidateDelegationRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getValidateDelegationMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service IdentityService.
   */
  public static final class IdentityServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<IdentityServiceFutureStub> {
    private IdentityServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected IdentityServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new IdentityServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.auth.v1.GetIdentityContextResponse> getIdentityContext(
        com.umar.grpc.auth.v1.GetIdentityContextRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetIdentityContextMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.auth.v1.ValidateDelegationResponse> validateDelegation(
        com.umar.grpc.auth.v1.ValidateDelegationRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getValidateDelegationMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_IDENTITY_CONTEXT = 0;
  private static final int METHODID_VALIDATE_DELEGATION = 1;

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
        case METHODID_GET_IDENTITY_CONTEXT:
          serviceImpl.getIdentityContext((com.umar.grpc.auth.v1.GetIdentityContextRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.auth.v1.GetIdentityContextResponse>) responseObserver);
          break;
        case METHODID_VALIDATE_DELEGATION:
          serviceImpl.validateDelegation((com.umar.grpc.auth.v1.ValidateDelegationRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.auth.v1.ValidateDelegationResponse>) responseObserver);
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
          getGetIdentityContextMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.auth.v1.GetIdentityContextRequest,
              com.umar.grpc.auth.v1.GetIdentityContextResponse>(
                service, METHODID_GET_IDENTITY_CONTEXT)))
        .addMethod(
          getValidateDelegationMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.auth.v1.ValidateDelegationRequest,
              com.umar.grpc.auth.v1.ValidateDelegationResponse>(
                service, METHODID_VALIDATE_DELEGATION)))
        .build();
  }

  private static abstract class IdentityServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    IdentityServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.grpc.auth.v1.AuthIdentityProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("IdentityService");
    }
  }

  private static final class IdentityServiceFileDescriptorSupplier
      extends IdentityServiceBaseDescriptorSupplier {
    IdentityServiceFileDescriptorSupplier() {}
  }

  private static final class IdentityServiceMethodDescriptorSupplier
      extends IdentityServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    IdentityServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (IdentityServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new IdentityServiceFileDescriptorSupplier())
              .addMethod(getGetIdentityContextMethod())
              .addMethod(getValidateDelegationMethod())
              .build();
        }
      }
    }
    return result;
  }
}
