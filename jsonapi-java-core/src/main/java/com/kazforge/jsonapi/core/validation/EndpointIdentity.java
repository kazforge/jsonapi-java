package com.kazforge.jsonapi.core.validation;

/**
 * Expected identity of the resource an HTTP update endpoint addresses.
 *
 * <p>Supplied by applications/adapters (from the request route) and compared by {@link
 * JsonApiDocumentValidator} against the primary resource {@code type} and {@code id} of an {@link
 * DocumentUsage#UPDATE_REQUEST} document. An absent expected identity on {@link ValidationContext}
 * disables the comparison; the library never derives it from HTTP concerns.
 */
public record EndpointIdentity(String type, String id) {

  public EndpointIdentity {
    type =
        LocalValidation.requireNonNull(
            type, "/endpointIdentity/type", "Endpoint identity type must not be null");
    id =
        LocalValidation.requireNonNull(
            id, "/endpointIdentity/id", "Endpoint identity id must not be null");
  }
}
