package com.umar.grpc.vault.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class VaultServiceGrpc {

  private VaultServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "vault.v1.VaultService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.TokenizeGrpcRequest,
      com.umar.grpc.vault.v1.TokenizeGrpcResponse> getTokenizeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Tokenize",
      requestType = com.umar.grpc.vault.v1.TokenizeGrpcRequest.class,
      responseType = com.umar.grpc.vault.v1.TokenizeGrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.TokenizeGrpcRequest,
      com.umar.grpc.vault.v1.TokenizeGrpcResponse> getTokenizeMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.TokenizeGrpcRequest, com.umar.grpc.vault.v1.TokenizeGrpcResponse> getTokenizeMethod;
    if ((getTokenizeMethod = VaultServiceGrpc.getTokenizeMethod) == null) {
      synchronized (VaultServiceGrpc.class) {
        if ((getTokenizeMethod = VaultServiceGrpc.getTokenizeMethod) == null) {
          VaultServiceGrpc.getTokenizeMethod = getTokenizeMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.vault.v1.TokenizeGrpcRequest, com.umar.grpc.vault.v1.TokenizeGrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Tokenize"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.TokenizeGrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.TokenizeGrpcResponse.getDefaultInstance()))
              .setSchemaDescriptor(new VaultServiceMethodDescriptorSupplier("Tokenize"))
              .build();
        }
      }
    }
    return getTokenizeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.DetokenizeGrpcRequest,
      com.umar.grpc.vault.v1.DetokenizeGrpcResponse> getDetokenizeMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Detokenize",
      requestType = com.umar.grpc.vault.v1.DetokenizeGrpcRequest.class,
      responseType = com.umar.grpc.vault.v1.DetokenizeGrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.DetokenizeGrpcRequest,
      com.umar.grpc.vault.v1.DetokenizeGrpcResponse> getDetokenizeMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.DetokenizeGrpcRequest, com.umar.grpc.vault.v1.DetokenizeGrpcResponse> getDetokenizeMethod;
    if ((getDetokenizeMethod = VaultServiceGrpc.getDetokenizeMethod) == null) {
      synchronized (VaultServiceGrpc.class) {
        if ((getDetokenizeMethod = VaultServiceGrpc.getDetokenizeMethod) == null) {
          VaultServiceGrpc.getDetokenizeMethod = getDetokenizeMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.vault.v1.DetokenizeGrpcRequest, com.umar.grpc.vault.v1.DetokenizeGrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Detokenize"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.DetokenizeGrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.DetokenizeGrpcResponse.getDefaultInstance()))
              .setSchemaDescriptor(new VaultServiceMethodDescriptorSupplier("Detokenize"))
              .build();
        }
      }
    }
    return getDetokenizeMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.HashGrpcRequest,
      com.umar.grpc.vault.v1.HashGrpcResponse> getHashMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Hash",
      requestType = com.umar.grpc.vault.v1.HashGrpcRequest.class,
      responseType = com.umar.grpc.vault.v1.HashGrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.HashGrpcRequest,
      com.umar.grpc.vault.v1.HashGrpcResponse> getHashMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.HashGrpcRequest, com.umar.grpc.vault.v1.HashGrpcResponse> getHashMethod;
    if ((getHashMethod = VaultServiceGrpc.getHashMethod) == null) {
      synchronized (VaultServiceGrpc.class) {
        if ((getHashMethod = VaultServiceGrpc.getHashMethod) == null) {
          VaultServiceGrpc.getHashMethod = getHashMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.vault.v1.HashGrpcRequest, com.umar.grpc.vault.v1.HashGrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Hash"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.HashGrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.HashGrpcResponse.getDefaultInstance()))
              .setSchemaDescriptor(new VaultServiceMethodDescriptorSupplier("Hash"))
              .build();
        }
      }
    }
    return getHashMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.SignGrpcRequest,
      com.umar.grpc.vault.v1.SignGrpcResponse> getSignMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "Sign",
      requestType = com.umar.grpc.vault.v1.SignGrpcRequest.class,
      responseType = com.umar.grpc.vault.v1.SignGrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.SignGrpcRequest,
      com.umar.grpc.vault.v1.SignGrpcResponse> getSignMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.SignGrpcRequest, com.umar.grpc.vault.v1.SignGrpcResponse> getSignMethod;
    if ((getSignMethod = VaultServiceGrpc.getSignMethod) == null) {
      synchronized (VaultServiceGrpc.class) {
        if ((getSignMethod = VaultServiceGrpc.getSignMethod) == null) {
          VaultServiceGrpc.getSignMethod = getSignMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.vault.v1.SignGrpcRequest, com.umar.grpc.vault.v1.SignGrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "Sign"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.SignGrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.SignGrpcResponse.getDefaultInstance()))
              .setSchemaDescriptor(new VaultServiceMethodDescriptorSupplier("Sign"))
              .build();
        }
      }
    }
    return getSignMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.TokenizeGrpcRequest,
      com.umar.grpc.vault.v1.TokenizeGrpcResponse> getTokenizeStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "TokenizeStream",
      requestType = com.umar.grpc.vault.v1.TokenizeGrpcRequest.class,
      responseType = com.umar.grpc.vault.v1.TokenizeGrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.TokenizeGrpcRequest,
      com.umar.grpc.vault.v1.TokenizeGrpcResponse> getTokenizeStreamMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.TokenizeGrpcRequest, com.umar.grpc.vault.v1.TokenizeGrpcResponse> getTokenizeStreamMethod;
    if ((getTokenizeStreamMethod = VaultServiceGrpc.getTokenizeStreamMethod) == null) {
      synchronized (VaultServiceGrpc.class) {
        if ((getTokenizeStreamMethod = VaultServiceGrpc.getTokenizeStreamMethod) == null) {
          VaultServiceGrpc.getTokenizeStreamMethod = getTokenizeStreamMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.vault.v1.TokenizeGrpcRequest, com.umar.grpc.vault.v1.TokenizeGrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "TokenizeStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.TokenizeGrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.TokenizeGrpcResponse.getDefaultInstance()))
              .setSchemaDescriptor(new VaultServiceMethodDescriptorSupplier("TokenizeStream"))
              .build();
        }
      }
    }
    return getTokenizeStreamMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.DetokenizeGrpcRequest,
      com.umar.grpc.vault.v1.DetokenizeGrpcResponse> getDetokenizeStreamMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "DetokenizeStream",
      requestType = com.umar.grpc.vault.v1.DetokenizeGrpcRequest.class,
      responseType = com.umar.grpc.vault.v1.DetokenizeGrpcResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.DetokenizeGrpcRequest,
      com.umar.grpc.vault.v1.DetokenizeGrpcResponse> getDetokenizeStreamMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.vault.v1.DetokenizeGrpcRequest, com.umar.grpc.vault.v1.DetokenizeGrpcResponse> getDetokenizeStreamMethod;
    if ((getDetokenizeStreamMethod = VaultServiceGrpc.getDetokenizeStreamMethod) == null) {
      synchronized (VaultServiceGrpc.class) {
        if ((getDetokenizeStreamMethod = VaultServiceGrpc.getDetokenizeStreamMethod) == null) {
          VaultServiceGrpc.getDetokenizeStreamMethod = getDetokenizeStreamMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.vault.v1.DetokenizeGrpcRequest, com.umar.grpc.vault.v1.DetokenizeGrpcResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "DetokenizeStream"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.DetokenizeGrpcRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.vault.v1.DetokenizeGrpcResponse.getDefaultInstance()))
              .setSchemaDescriptor(new VaultServiceMethodDescriptorSupplier("DetokenizeStream"))
              .build();
        }
      }
    }
    return getDetokenizeStreamMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static VaultServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VaultServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VaultServiceStub>() {
        @java.lang.Override
        public VaultServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VaultServiceStub(channel, callOptions);
        }
      };
    return VaultServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static VaultServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VaultServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VaultServiceBlockingV2Stub>() {
        @java.lang.Override
        public VaultServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VaultServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return VaultServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static VaultServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VaultServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VaultServiceBlockingStub>() {
        @java.lang.Override
        public VaultServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VaultServiceBlockingStub(channel, callOptions);
        }
      };
    return VaultServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static VaultServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<VaultServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<VaultServiceFutureStub>() {
        @java.lang.Override
        public VaultServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new VaultServiceFutureStub(channel, callOptions);
        }
      };
    return VaultServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void tokenize(com.umar.grpc.vault.v1.TokenizeGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getTokenizeMethod(), responseObserver);
    }

    /**
     */
    default void detokenize(com.umar.grpc.vault.v1.DetokenizeGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getDetokenizeMethod(), responseObserver);
    }

    /**
     */
    default void hash(com.umar.grpc.vault.v1.HashGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.HashGrpcResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getHashMethod(), responseObserver);
    }

    /**
     */
    default void sign(com.umar.grpc.vault.v1.SignGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.SignGrpcResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getSignMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcRequest> tokenizeStream(
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcResponse> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getTokenizeStreamMethod(), responseObserver);
    }

    /**
     */
    default io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcRequest> detokenizeStream(
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcResponse> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getDetokenizeStreamMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service VaultService.
   */
  public static abstract class VaultServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return VaultServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service VaultService.
   */
  public static final class VaultServiceStub
      extends io.grpc.stub.AbstractAsyncStub<VaultServiceStub> {
    private VaultServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VaultServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VaultServiceStub(channel, callOptions);
    }

    /**
     */
    public void tokenize(com.umar.grpc.vault.v1.TokenizeGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getTokenizeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void detokenize(com.umar.grpc.vault.v1.DetokenizeGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getDetokenizeMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void hash(com.umar.grpc.vault.v1.HashGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.HashGrpcResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getHashMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void sign(com.umar.grpc.vault.v1.SignGrpcRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.SignGrpcResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getSignMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcRequest> tokenizeStream(
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcResponse> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getTokenizeStreamMethod(), getCallOptions()), responseObserver);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcRequest> detokenizeStream(
        io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcResponse> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getDetokenizeStreamMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service VaultService.
   */
  public static final class VaultServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<VaultServiceBlockingV2Stub> {
    private VaultServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VaultServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VaultServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.vault.v1.TokenizeGrpcResponse tokenize(com.umar.grpc.vault.v1.TokenizeGrpcRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getTokenizeMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.vault.v1.DetokenizeGrpcResponse detokenize(com.umar.grpc.vault.v1.DetokenizeGrpcRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getDetokenizeMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.vault.v1.HashGrpcResponse hash(com.umar.grpc.vault.v1.HashGrpcRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getHashMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.vault.v1.SignGrpcResponse sign(com.umar.grpc.vault.v1.SignGrpcRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getSignMethod(), getCallOptions(), request);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<com.umar.grpc.vault.v1.TokenizeGrpcRequest, com.umar.grpc.vault.v1.TokenizeGrpcResponse>
        tokenizeStream() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getTokenizeStreamMethod(), getCallOptions());
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<com.umar.grpc.vault.v1.DetokenizeGrpcRequest, com.umar.grpc.vault.v1.DetokenizeGrpcResponse>
        detokenizeStream() {
      return io.grpc.stub.ClientCalls.blockingBidiStreamingCall(
          getChannel(), getDetokenizeStreamMethod(), getCallOptions());
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service VaultService.
   */
  public static final class VaultServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<VaultServiceBlockingStub> {
    private VaultServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VaultServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VaultServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.vault.v1.TokenizeGrpcResponse tokenize(com.umar.grpc.vault.v1.TokenizeGrpcRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getTokenizeMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.vault.v1.DetokenizeGrpcResponse detokenize(com.umar.grpc.vault.v1.DetokenizeGrpcRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getDetokenizeMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.vault.v1.HashGrpcResponse hash(com.umar.grpc.vault.v1.HashGrpcRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getHashMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.vault.v1.SignGrpcResponse sign(com.umar.grpc.vault.v1.SignGrpcRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getSignMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service VaultService.
   */
  public static final class VaultServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<VaultServiceFutureStub> {
    private VaultServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected VaultServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new VaultServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.vault.v1.TokenizeGrpcResponse> tokenize(
        com.umar.grpc.vault.v1.TokenizeGrpcRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getTokenizeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.vault.v1.DetokenizeGrpcResponse> detokenize(
        com.umar.grpc.vault.v1.DetokenizeGrpcRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getDetokenizeMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.vault.v1.HashGrpcResponse> hash(
        com.umar.grpc.vault.v1.HashGrpcRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getHashMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.vault.v1.SignGrpcResponse> sign(
        com.umar.grpc.vault.v1.SignGrpcRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getSignMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_TOKENIZE = 0;
  private static final int METHODID_DETOKENIZE = 1;
  private static final int METHODID_HASH = 2;
  private static final int METHODID_SIGN = 3;
  private static final int METHODID_TOKENIZE_STREAM = 4;
  private static final int METHODID_DETOKENIZE_STREAM = 5;

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
        case METHODID_TOKENIZE:
          serviceImpl.tokenize((com.umar.grpc.vault.v1.TokenizeGrpcRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcResponse>) responseObserver);
          break;
        case METHODID_DETOKENIZE:
          serviceImpl.detokenize((com.umar.grpc.vault.v1.DetokenizeGrpcRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcResponse>) responseObserver);
          break;
        case METHODID_HASH:
          serviceImpl.hash((com.umar.grpc.vault.v1.HashGrpcRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.HashGrpcResponse>) responseObserver);
          break;
        case METHODID_SIGN:
          serviceImpl.sign((com.umar.grpc.vault.v1.SignGrpcRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.SignGrpcResponse>) responseObserver);
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
        case METHODID_TOKENIZE_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.tokenizeStream(
              (io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.TokenizeGrpcResponse>) responseObserver);
        case METHODID_DETOKENIZE_STREAM:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.detokenizeStream(
              (io.grpc.stub.StreamObserver<com.umar.grpc.vault.v1.DetokenizeGrpcResponse>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  public static final io.grpc.ServerServiceDefinition bindService(AsyncService service) {
    return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
        .addMethod(
          getTokenizeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.vault.v1.TokenizeGrpcRequest,
              com.umar.grpc.vault.v1.TokenizeGrpcResponse>(
                service, METHODID_TOKENIZE)))
        .addMethod(
          getDetokenizeMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.vault.v1.DetokenizeGrpcRequest,
              com.umar.grpc.vault.v1.DetokenizeGrpcResponse>(
                service, METHODID_DETOKENIZE)))
        .addMethod(
          getHashMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.vault.v1.HashGrpcRequest,
              com.umar.grpc.vault.v1.HashGrpcResponse>(
                service, METHODID_HASH)))
        .addMethod(
          getSignMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.vault.v1.SignGrpcRequest,
              com.umar.grpc.vault.v1.SignGrpcResponse>(
                service, METHODID_SIGN)))
        .addMethod(
          getTokenizeStreamMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.umar.grpc.vault.v1.TokenizeGrpcRequest,
              com.umar.grpc.vault.v1.TokenizeGrpcResponse>(
                service, METHODID_TOKENIZE_STREAM)))
        .addMethod(
          getDetokenizeStreamMethod(),
          io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
            new MethodHandlers<
              com.umar.grpc.vault.v1.DetokenizeGrpcRequest,
              com.umar.grpc.vault.v1.DetokenizeGrpcResponse>(
                service, METHODID_DETOKENIZE_STREAM)))
        .build();
  }

  private static abstract class VaultServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    VaultServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.grpc.vault.v1.VaultServiceProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("VaultService");
    }
  }

  private static final class VaultServiceFileDescriptorSupplier
      extends VaultServiceBaseDescriptorSupplier {
    VaultServiceFileDescriptorSupplier() {}
  }

  private static final class VaultServiceMethodDescriptorSupplier
      extends VaultServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    VaultServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (VaultServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new VaultServiceFileDescriptorSupplier())
              .addMethod(getTokenizeMethod())
              .addMethod(getDetokenizeMethod())
              .addMethod(getHashMethod())
              .addMethod(getSignMethod())
              .addMethod(getTokenizeStreamMethod())
              .addMethod(getDetokenizeStreamMethod())
              .build();
        }
      }
    }
    return result;
  }
}
