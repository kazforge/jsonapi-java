package com.kazforge.jsonapi.mapping.contract;

import com.kazforge.jsonapi.core.model.ResourceObject;

/** Black-box adapter for the shared Core-to-application binding contract. */
public interface BindingContractAdapter {

  <T> T fromResource(ResourceObject resource, Class<T> targetClass);
}
