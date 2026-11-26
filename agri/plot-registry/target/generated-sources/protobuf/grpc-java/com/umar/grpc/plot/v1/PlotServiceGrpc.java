package com.umar.grpc.plot.v1;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class PlotServiceGrpc {

  private PlotServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "plot.v1.PlotService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.ListFarmerPlotsRequest,
      com.umar.grpc.plot.v1.ListFarmerPlotsResponse> getListFarmerPlotsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListFarmerPlots",
      requestType = com.umar.grpc.plot.v1.ListFarmerPlotsRequest.class,
      responseType = com.umar.grpc.plot.v1.ListFarmerPlotsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.ListFarmerPlotsRequest,
      com.umar.grpc.plot.v1.ListFarmerPlotsResponse> getListFarmerPlotsMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.ListFarmerPlotsRequest, com.umar.grpc.plot.v1.ListFarmerPlotsResponse> getListFarmerPlotsMethod;
    if ((getListFarmerPlotsMethod = PlotServiceGrpc.getListFarmerPlotsMethod) == null) {
      synchronized (PlotServiceGrpc.class) {
        if ((getListFarmerPlotsMethod = PlotServiceGrpc.getListFarmerPlotsMethod) == null) {
          PlotServiceGrpc.getListFarmerPlotsMethod = getListFarmerPlotsMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.plot.v1.ListFarmerPlotsRequest, com.umar.grpc.plot.v1.ListFarmerPlotsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListFarmerPlots"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.plot.v1.ListFarmerPlotsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.plot.v1.ListFarmerPlotsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PlotServiceMethodDescriptorSupplier("ListFarmerPlots"))
              .build();
        }
      }
    }
    return getListFarmerPlotsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.StreamPlotsWithinRequest,
      com.umar.grpc.plot.v1.StreamPlotsWithinResponse> getStreamPlotsWithinMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "StreamPlotsWithin",
      requestType = com.umar.grpc.plot.v1.StreamPlotsWithinRequest.class,
      responseType = com.umar.grpc.plot.v1.StreamPlotsWithinResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
  public static io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.StreamPlotsWithinRequest,
      com.umar.grpc.plot.v1.StreamPlotsWithinResponse> getStreamPlotsWithinMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.StreamPlotsWithinRequest, com.umar.grpc.plot.v1.StreamPlotsWithinResponse> getStreamPlotsWithinMethod;
    if ((getStreamPlotsWithinMethod = PlotServiceGrpc.getStreamPlotsWithinMethod) == null) {
      synchronized (PlotServiceGrpc.class) {
        if ((getStreamPlotsWithinMethod = PlotServiceGrpc.getStreamPlotsWithinMethod) == null) {
          PlotServiceGrpc.getStreamPlotsWithinMethod = getStreamPlotsWithinMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.plot.v1.StreamPlotsWithinRequest, com.umar.grpc.plot.v1.StreamPlotsWithinResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.SERVER_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "StreamPlotsWithin"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.plot.v1.StreamPlotsWithinRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.plot.v1.StreamPlotsWithinResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PlotServiceMethodDescriptorSupplier("StreamPlotsWithin"))
              .build();
        }
      }
    }
    return getStreamPlotsWithinMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.GetPlotDetailsRequest,
      com.umar.grpc.plot.v1.GetPlotDetailsResponse> getGetPlotDetailsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPlotDetails",
      requestType = com.umar.grpc.plot.v1.GetPlotDetailsRequest.class,
      responseType = com.umar.grpc.plot.v1.GetPlotDetailsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.GetPlotDetailsRequest,
      com.umar.grpc.plot.v1.GetPlotDetailsResponse> getGetPlotDetailsMethod() {
    io.grpc.MethodDescriptor<com.umar.grpc.plot.v1.GetPlotDetailsRequest, com.umar.grpc.plot.v1.GetPlotDetailsResponse> getGetPlotDetailsMethod;
    if ((getGetPlotDetailsMethod = PlotServiceGrpc.getGetPlotDetailsMethod) == null) {
      synchronized (PlotServiceGrpc.class) {
        if ((getGetPlotDetailsMethod = PlotServiceGrpc.getGetPlotDetailsMethod) == null) {
          PlotServiceGrpc.getGetPlotDetailsMethod = getGetPlotDetailsMethod =
              io.grpc.MethodDescriptor.<com.umar.grpc.plot.v1.GetPlotDetailsRequest, com.umar.grpc.plot.v1.GetPlotDetailsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPlotDetails"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.plot.v1.GetPlotDetailsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.grpc.plot.v1.GetPlotDetailsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PlotServiceMethodDescriptorSupplier("GetPlotDetails"))
              .build();
        }
      }
    }
    return getGetPlotDetailsMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PlotServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PlotServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PlotServiceStub>() {
        @java.lang.Override
        public PlotServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PlotServiceStub(channel, callOptions);
        }
      };
    return PlotServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static PlotServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PlotServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PlotServiceBlockingV2Stub>() {
        @java.lang.Override
        public PlotServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PlotServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return PlotServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PlotServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PlotServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PlotServiceBlockingStub>() {
        @java.lang.Override
        public PlotServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PlotServiceBlockingStub(channel, callOptions);
        }
      };
    return PlotServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PlotServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PlotServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PlotServiceFutureStub>() {
        @java.lang.Override
        public PlotServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PlotServiceFutureStub(channel, callOptions);
        }
      };
    return PlotServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void listFarmerPlots(com.umar.grpc.plot.v1.ListFarmerPlotsRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.ListFarmerPlotsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListFarmerPlotsMethod(), responseObserver);
    }

    /**
     */
    default void streamPlotsWithin(com.umar.grpc.plot.v1.StreamPlotsWithinRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.StreamPlotsWithinResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getStreamPlotsWithinMethod(), responseObserver);
    }

    /**
     */
    default void getPlotDetails(com.umar.grpc.plot.v1.GetPlotDetailsRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.GetPlotDetailsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPlotDetailsMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PlotService.
   */
  public static abstract class PlotServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PlotServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PlotService.
   */
  public static final class PlotServiceStub
      extends io.grpc.stub.AbstractAsyncStub<PlotServiceStub> {
    private PlotServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PlotServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PlotServiceStub(channel, callOptions);
    }

    /**
     */
    public void listFarmerPlots(com.umar.grpc.plot.v1.ListFarmerPlotsRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.ListFarmerPlotsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListFarmerPlotsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void streamPlotsWithin(com.umar.grpc.plot.v1.StreamPlotsWithinRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.StreamPlotsWithinResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncServerStreamingCall(
          getChannel().newCall(getStreamPlotsWithinMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void getPlotDetails(com.umar.grpc.plot.v1.GetPlotDetailsRequest request,
        io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.GetPlotDetailsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPlotDetailsMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PlotService.
   */
  public static final class PlotServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<PlotServiceBlockingV2Stub> {
    private PlotServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PlotServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PlotServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.plot.v1.ListFarmerPlotsResponse listFarmerPlots(com.umar.grpc.plot.v1.ListFarmerPlotsRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getListFarmerPlotsMethod(), getCallOptions(), request);
    }

    /**
     */
    @io.grpc.ExperimentalApi("https://github.com/grpc/grpc-java/issues/10918")
    public io.grpc.stub.BlockingClientCall<?, com.umar.grpc.plot.v1.StreamPlotsWithinResponse>
        streamPlotsWithin(com.umar.grpc.plot.v1.StreamPlotsWithinRequest request) {
      return io.grpc.stub.ClientCalls.blockingV2ServerStreamingCall(
          getChannel(), getStreamPlotsWithinMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.plot.v1.GetPlotDetailsResponse getPlotDetails(com.umar.grpc.plot.v1.GetPlotDetailsRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetPlotDetailsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service PlotService.
   */
  public static final class PlotServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PlotServiceBlockingStub> {
    private PlotServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PlotServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PlotServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.grpc.plot.v1.ListFarmerPlotsResponse listFarmerPlots(com.umar.grpc.plot.v1.ListFarmerPlotsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListFarmerPlotsMethod(), getCallOptions(), request);
    }

    /**
     */
    public java.util.Iterator<com.umar.grpc.plot.v1.StreamPlotsWithinResponse> streamPlotsWithin(
        com.umar.grpc.plot.v1.StreamPlotsWithinRequest request) {
      return io.grpc.stub.ClientCalls.blockingServerStreamingCall(
          getChannel(), getStreamPlotsWithinMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.grpc.plot.v1.GetPlotDetailsResponse getPlotDetails(com.umar.grpc.plot.v1.GetPlotDetailsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPlotDetailsMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PlotService.
   */
  public static final class PlotServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<PlotServiceFutureStub> {
    private PlotServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PlotServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PlotServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.plot.v1.ListFarmerPlotsResponse> listFarmerPlots(
        com.umar.grpc.plot.v1.ListFarmerPlotsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListFarmerPlotsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.grpc.plot.v1.GetPlotDetailsResponse> getPlotDetails(
        com.umar.grpc.plot.v1.GetPlotDetailsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPlotDetailsMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_LIST_FARMER_PLOTS = 0;
  private static final int METHODID_STREAM_PLOTS_WITHIN = 1;
  private static final int METHODID_GET_PLOT_DETAILS = 2;

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
        case METHODID_LIST_FARMER_PLOTS:
          serviceImpl.listFarmerPlots((com.umar.grpc.plot.v1.ListFarmerPlotsRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.ListFarmerPlotsResponse>) responseObserver);
          break;
        case METHODID_STREAM_PLOTS_WITHIN:
          serviceImpl.streamPlotsWithin((com.umar.grpc.plot.v1.StreamPlotsWithinRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.StreamPlotsWithinResponse>) responseObserver);
          break;
        case METHODID_GET_PLOT_DETAILS:
          serviceImpl.getPlotDetails((com.umar.grpc.plot.v1.GetPlotDetailsRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.grpc.plot.v1.GetPlotDetailsResponse>) responseObserver);
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
          getListFarmerPlotsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.plot.v1.ListFarmerPlotsRequest,
              com.umar.grpc.plot.v1.ListFarmerPlotsResponse>(
                service, METHODID_LIST_FARMER_PLOTS)))
        .addMethod(
          getStreamPlotsWithinMethod(),
          io.grpc.stub.ServerCalls.asyncServerStreamingCall(
            new MethodHandlers<
              com.umar.grpc.plot.v1.StreamPlotsWithinRequest,
              com.umar.grpc.plot.v1.StreamPlotsWithinResponse>(
                service, METHODID_STREAM_PLOTS_WITHIN)))
        .addMethod(
          getGetPlotDetailsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.grpc.plot.v1.GetPlotDetailsRequest,
              com.umar.grpc.plot.v1.GetPlotDetailsResponse>(
                service, METHODID_GET_PLOT_DETAILS)))
        .build();
  }

  private static abstract class PlotServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PlotServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.grpc.plot.v1.PlotRegistryProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PlotService");
    }
  }

  private static final class PlotServiceFileDescriptorSupplier
      extends PlotServiceBaseDescriptorSupplier {
    PlotServiceFileDescriptorSupplier() {}
  }

  private static final class PlotServiceMethodDescriptorSupplier
      extends PlotServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PlotServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (PlotServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PlotServiceFileDescriptorSupplier())
              .addMethod(getListFarmerPlotsMethod())
              .addMethod(getStreamPlotsWithinMethod())
              .addMethod(getGetPlotDetailsMethod())
              .build();
        }
      }
    }
    return result;
  }
}
