package com.umar.policy;

import java.lang.reflect.Method;
import java.util.List;

public interface PolicyResourceResolver {

    boolean supports(Method method);
    List<PolicyResource> resolve(Object[] arguments);

}
