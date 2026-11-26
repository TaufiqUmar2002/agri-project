package com.umar.policy.grpc;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 */
@io.grpc.stub.annotations.GrpcGenerated
public final class PolicyServiceGrpc {

  private PolicyServiceGrpc() {}

  public static final java.lang.String SERVICE_NAME = "policy.v1.PolicyService";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<com.umar.policy.grpc.GetPolicyTermsRequest,
      com.umar.policy.grpc.GetPolicyTermsResponse> getGetPolicyTermsMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "GetPolicyTerms",
      requestType = com.umar.policy.grpc.GetPolicyTermsRequest.class,
      responseType = com.umar.policy.grpc.GetPolicyTermsResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.policy.grpc.GetPolicyTermsRequest,
      com.umar.policy.grpc.GetPolicyTermsResponse> getGetPolicyTermsMethod() {
    io.grpc.MethodDescriptor<com.umar.policy.grpc.GetPolicyTermsRequest, com.umar.policy.grpc.GetPolicyTermsResponse> getGetPolicyTermsMethod;
    if ((getGetPolicyTermsMethod = PolicyServiceGrpc.getGetPolicyTermsMethod) == null) {
      synchronized (PolicyServiceGrpc.class) {
        if ((getGetPolicyTermsMethod = PolicyServiceGrpc.getGetPolicyTermsMethod) == null) {
          PolicyServiceGrpc.getGetPolicyTermsMethod = getGetPolicyTermsMethod =
              io.grpc.MethodDescriptor.<com.umar.policy.grpc.GetPolicyTermsRequest, com.umar.policy.grpc.GetPolicyTermsResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "GetPolicyTerms"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.policy.grpc.GetPolicyTermsRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.policy.grpc.GetPolicyTermsResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PolicyServiceMethodDescriptorSupplier("GetPolicyTerms"))
              .build();
        }
      }
    }
    return getGetPolicyTermsMethod;
  }

  private static volatile io.grpc.MethodDescriptor<com.umar.policy.grpc.ListFarmerPoliciesRequest,
      com.umar.policy.grpc.ListFarmerPoliciesResponse> getListFarmerPoliciesMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "ListFarmerPolicies",
      requestType = com.umar.policy.grpc.ListFarmerPoliciesRequest.class,
      responseType = com.umar.policy.grpc.ListFarmerPoliciesResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.UNARY)
  public static io.grpc.MethodDescriptor<com.umar.policy.grpc.ListFarmerPoliciesRequest,
      com.umar.policy.grpc.ListFarmerPoliciesResponse> getListFarmerPoliciesMethod() {
    io.grpc.MethodDescriptor<com.umar.policy.grpc.ListFarmerPoliciesRequest, com.umar.policy.grpc.ListFarmerPoliciesResponse> getListFarmerPoliciesMethod;
    if ((getListFarmerPoliciesMethod = PolicyServiceGrpc.getListFarmerPoliciesMethod) == null) {
      synchronized (PolicyServiceGrpc.class) {
        if ((getListFarmerPoliciesMethod = PolicyServiceGrpc.getListFarmerPoliciesMethod) == null) {
          PolicyServiceGrpc.getListFarmerPoliciesMethod = getListFarmerPoliciesMethod =
              io.grpc.MethodDescriptor.<com.umar.policy.grpc.ListFarmerPoliciesRequest, com.umar.policy.grpc.ListFarmerPoliciesResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.UNARY)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "ListFarmerPolicies"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.policy.grpc.ListFarmerPoliciesRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  com.umar.policy.grpc.ListFarmerPoliciesResponse.getDefaultInstance()))
              .setSchemaDescriptor(new PolicyServiceMethodDescriptorSupplier("ListFarmerPolicies"))
              .build();
        }
      }
    }
    return getListFarmerPoliciesMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static PolicyServiceStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PolicyServiceStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PolicyServiceStub>() {
        @java.lang.Override
        public PolicyServiceStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PolicyServiceStub(channel, callOptions);
        }
      };
    return PolicyServiceStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports all types of calls on the service
   */
  public static PolicyServiceBlockingV2Stub newBlockingV2Stub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PolicyServiceBlockingV2Stub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PolicyServiceBlockingV2Stub>() {
        @java.lang.Override
        public PolicyServiceBlockingV2Stub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PolicyServiceBlockingV2Stub(channel, callOptions);
        }
      };
    return PolicyServiceBlockingV2Stub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static PolicyServiceBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PolicyServiceBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PolicyServiceBlockingStub>() {
        @java.lang.Override
        public PolicyServiceBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PolicyServiceBlockingStub(channel, callOptions);
        }
      };
    return PolicyServiceBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static PolicyServiceFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<PolicyServiceFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<PolicyServiceFutureStub>() {
        @java.lang.Override
        public PolicyServiceFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new PolicyServiceFutureStub(channel, callOptions);
        }
      };
    return PolicyServiceFutureStub.newStub(factory, channel);
  }

  /**
   */
  public interface AsyncService {

    /**
     */
    default void getPolicyTerms(com.umar.policy.grpc.GetPolicyTermsRequest request,
        io.grpc.stub.StreamObserver<com.umar.policy.grpc.GetPolicyTermsResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getGetPolicyTermsMethod(), responseObserver);
    }

    /**
     */
    default void listFarmerPolicies(com.umar.policy.grpc.ListFarmerPoliciesRequest request,
        io.grpc.stub.StreamObserver<com.umar.policy.grpc.ListFarmerPoliciesResponse> responseObserver) {
      io.grpc.stub.ServerCalls.asyncUnimplementedUnaryCall(getListFarmerPoliciesMethod(), responseObserver);
    }
  }

  /**
   * Base class for the server implementation of the service PolicyService.
   */
  public static abstract class PolicyServiceImplBase
      implements io.grpc.BindableService, AsyncService {

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return PolicyServiceGrpc.bindService(this);
    }
  }

  /**
   * A stub to allow clients to do asynchronous rpc calls to service PolicyService.
   */
  public static final class PolicyServiceStub
      extends io.grpc.stub.AbstractAsyncStub<PolicyServiceStub> {
    private PolicyServiceStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PolicyServiceStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PolicyServiceStub(channel, callOptions);
    }

    /**
     */
    public void getPolicyTerms(com.umar.policy.grpc.GetPolicyTermsRequest request,
        io.grpc.stub.StreamObserver<com.umar.policy.grpc.GetPolicyTermsResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getGetPolicyTermsMethod(), getCallOptions()), request, responseObserver);
    }

    /**
     */
    public void listFarmerPolicies(com.umar.policy.grpc.ListFarmerPoliciesRequest request,
        io.grpc.stub.StreamObserver<com.umar.policy.grpc.ListFarmerPoliciesResponse> responseObserver) {
      io.grpc.stub.ClientCalls.asyncUnaryCall(
          getChannel().newCall(getListFarmerPoliciesMethod(), getCallOptions()), request, responseObserver);
    }
  }

  /**
   * A stub to allow clients to do synchronous rpc calls to service PolicyService.
   */
  public static final class PolicyServiceBlockingV2Stub
      extends io.grpc.stub.AbstractBlockingStub<PolicyServiceBlockingV2Stub> {
    private PolicyServiceBlockingV2Stub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PolicyServiceBlockingV2Stub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PolicyServiceBlockingV2Stub(channel, callOptions);
    }

    /**
     */
    public com.umar.policy.grpc.GetPolicyTermsResponse getPolicyTerms(com.umar.policy.grpc.GetPolicyTermsRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getGetPolicyTermsMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.policy.grpc.ListFarmerPoliciesResponse listFarmerPolicies(com.umar.policy.grpc.ListFarmerPoliciesRequest request) throws io.grpc.StatusException {
      return io.grpc.stub.ClientCalls.blockingV2UnaryCall(
          getChannel(), getListFarmerPoliciesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do limited synchronous rpc calls to service PolicyService.
   */
  public static final class PolicyServiceBlockingStub
      extends io.grpc.stub.AbstractBlockingStub<PolicyServiceBlockingStub> {
    private PolicyServiceBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PolicyServiceBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PolicyServiceBlockingStub(channel, callOptions);
    }

    /**
     */
    public com.umar.policy.grpc.GetPolicyTermsResponse getPolicyTerms(com.umar.policy.grpc.GetPolicyTermsRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getGetPolicyTermsMethod(), getCallOptions(), request);
    }

    /**
     */
    public com.umar.policy.grpc.ListFarmerPoliciesResponse listFarmerPolicies(com.umar.policy.grpc.ListFarmerPoliciesRequest request) {
      return io.grpc.stub.ClientCalls.blockingUnaryCall(
          getChannel(), getListFarmerPoliciesMethod(), getCallOptions(), request);
    }
  }

  /**
   * A stub to allow clients to do ListenableFuture-style rpc calls to service PolicyService.
   */
  public static final class PolicyServiceFutureStub
      extends io.grpc.stub.AbstractFutureStub<PolicyServiceFutureStub> {
    private PolicyServiceFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected PolicyServiceFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new PolicyServiceFutureStub(channel, callOptions);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.policy.grpc.GetPolicyTermsResponse> getPolicyTerms(
        com.umar.policy.grpc.GetPolicyTermsRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getGetPolicyTermsMethod(), getCallOptions()), request);
    }

    /**
     */
    public com.google.common.util.concurrent.ListenableFuture<com.umar.policy.grpc.ListFarmerPoliciesResponse> listFarmerPolicies(
        com.umar.policy.grpc.ListFarmerPoliciesRequest request) {
      return io.grpc.stub.ClientCalls.futureUnaryCall(
          getChannel().newCall(getListFarmerPoliciesMethod(), getCallOptions()), request);
    }
  }

  private static final int METHODID_GET_POLICY_TERMS = 0;
  private static final int METHODID_LIST_FARMER_POLICIES = 1;

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
        case METHODID_GET_POLICY_TERMS:
          serviceImpl.getPolicyTerms((com.umar.policy.grpc.GetPolicyTermsRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.policy.grpc.GetPolicyTermsResponse>) responseObserver);
          break;
        case METHODID_LIST_FARMER_POLICIES:
          serviceImpl.listFarmerPolicies((com.umar.policy.grpc.ListFarmerPoliciesRequest) request,
              (io.grpc.stub.StreamObserver<com.umar.policy.grpc.ListFarmerPoliciesResponse>) responseObserver);
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
          getGetPolicyTermsMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.policy.grpc.GetPolicyTermsRequest,
              com.umar.policy.grpc.GetPolicyTermsResponse>(
                service, METHODID_GET_POLICY_TERMS)))
        .addMethod(
          getListFarmerPoliciesMethod(),
          io.grpc.stub.ServerCalls.asyncUnaryCall(
            new MethodHandlers<
              com.umar.policy.grpc.ListFarmerPoliciesRequest,
              com.umar.policy.grpc.ListFarmerPoliciesResponse>(
                service, METHODID_LIST_FARMER_POLICIES)))
        .build();
  }

  private static abstract class PolicyServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    PolicyServiceBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return com.umar.policy.grpc.PolicyProto.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("PolicyService");
    }
  }

  private static final class PolicyServiceFileDescriptorSupplier
      extends PolicyServiceBaseDescriptorSupplier {
    PolicyServiceFileDescriptorSupplier() {}
  }

  private static final class PolicyServiceMethodDescriptorSupplier
      extends PolicyServiceBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final java.lang.String methodName;

    PolicyServiceMethodDescriptorSupplier(java.lang.String methodName) {
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
      synchronized (PolicyServiceGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new PolicyServiceFileDescriptorSupplier())
              .addMethod(getGetPolicyTermsMethod())
              .addMethod(getListFarmerPoliciesMethod())
              .build();
        }
      }
    }
    return result;
  }
}
