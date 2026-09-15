package com.kazforge.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.kazforge.jsonapi.jackson.mapping.RelationshipLinkage;
import com.kazforge.jsonapi.jackson.patch.PatchPresence;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Adapter-local {@code JavaType} shape operations shared by the write mapping, relationship
 * linkage, PATCH conversion, and inclusion traversal.
 *
 * <p>Writer-independent type tests live here so relationship and conversion support does not call
 * back into {@link DomainResourceWriter} for graph shape.
 */
final class MappingTypeSupport {

  private MappingTypeSupport() {}

  static boolean isToManyType(JavaType type) {
    if (type.isArrayType()) {
      return true;
    }
    if (type.isCollectionLikeType()) {
      return true;
    }
    return type.isTypeOrSubTypeOf(Iterable.class);
  }

  static @Nullable JavaType resolveContentType(JavaType type) {
    if (type.isArrayType() || type.isCollectionLikeType()) {
      return type.getContentType();
    }
    if (type.containedTypeCount() > 0) {
      return type.containedType(0);
    }
    return null;
  }

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
    if (isToManyType(unwrapped)) {
      JavaType content = resolveContentType(unwrapped);
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

  /**
   * The JavaType against which ordinary target conversion runs. For a wrapper property this is
   * {@code T} (or a collection/array of {@code T}); otherwise the original property type.
   */
  static JavaType targetMappingType(JavaType propertyType, TypeFactory typeFactory) {
    JavaType unwrapped = unwrapTransportWrappers(propertyType);
    JavaType linkageType = linkageJavaType(unwrapped);
    if (linkageType == null) {
      return propertyType;
    }
    JavaType target = linkageTargetType(linkageType);
    if (!isToManyType(unwrapped)) {
      return target;
    }
    if (unwrapped.isArrayType()) {
      return typeFactory.constructArrayType(target);
    }
    Class<?> raw = unwrapped.getRawClass();
    if (Set.class.isAssignableFrom(raw)) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Class<? extends Collection> setType = (Class<? extends Collection>) raw;
      return typeFactory.constructCollectionType(setType, target);
    }
    return typeFactory.constructCollectionType(List.class, target);
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
}
