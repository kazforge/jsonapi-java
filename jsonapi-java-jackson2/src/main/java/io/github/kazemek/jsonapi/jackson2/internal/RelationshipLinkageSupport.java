package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import io.github.kazemek.jsonapi.jackson.mapping.RelationshipLinkage;
import io.github.kazemek.jsonapi.jackson.patch.PatchPresence;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Write-side relationship linkage helpers: {@link RelationshipLinkage} type detection, wrapper
 * unwrapping, and resource-relative diagnostic locations (ADR-017, ADR-018).
 */
final class RelationshipLinkageSupport {

  private RelationshipLinkageSupport() {}

  static boolean isLinkageType(JavaType type) {
    return type.getRawClass() == RelationshipLinkage.class;
  }

  /**
   * Returns the {@link RelationshipLinkage} JavaType of a relationship property, or {@code null}
   * when the property is an ordinary target. Looks through one {@link Optional} and, for to-many
   * properties, through the collection/array content type.
   */
  static @Nullable JavaType linkageJavaType(JavaType propertyType) {
    JavaType unwrapped = unwrapTransportWrappers(propertyType);
    if (isLinkageType(unwrapped)) {
      return unwrapped;
    }
    if (DomainResourceWriter.isToManyType(unwrapped)) {
      JavaType content = DomainResourceWriter.resolveContentType(unwrapped);
      if (content != null) {
        JavaType contentUnwrapped = unwrapOptionalType(content);
        if (isLinkageType(contentUnwrapped)) {
          return contentUnwrapped;
        }
      }
    }
    return null;
  }

  static JavaType linkageTargetType(JavaType linkageType) {
    return linkageType.containedType(0);
  }

  static JavaType linkageMetaType(JavaType linkageType) {
    return linkageType.containedType(1);
  }

  static JavaType unwrapTransportWrappers(JavaType type) {
    JavaType current = type;
    if (current.getRawClass() == PatchPresence.class && current.containedTypeCount() == 1) {
      current = current.containedType(0);
    }
    return unwrapOptionalType(current);
  }

  static JavaType unwrapOptionalType(JavaType type) {
    if (type.isTypeOrSubTypeOf(Optional.class) && type.containedTypeCount() == 1) {
      return type.containedType(0);
    }
    return type;
  }

  static MappingLocation relationshipLocation(MappingPropertyView property) {
    return MappingLocation.of(JsonApiMembers.RELATIONSHIPS, property.jsonapiName(), "data");
  }
}
