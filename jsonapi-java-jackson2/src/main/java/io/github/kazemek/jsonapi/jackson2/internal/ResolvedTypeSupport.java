package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import io.github.kazemek.jsonapi.jackson.diagnostic.MappingLocation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Write-side checks for generic member declarations whose bindings are required by serialization.
 */
final class ResolvedTypeSupport {

  private ResolvedTypeSupport() {}

  static @Nullable MappingProperty findUnresolvedProperty(
      ResourceMapping mapping, JavaType declaredType) {
    for (MappingProperty property : allProperties(mapping)) {
      if (hasUnresolvedMemberType(declaredType, property)) {
        return property;
      }
    }
    return null;
  }

  static MappingLocation location(MappingProperty property) {
    return switch (property.role()) {
      case ID -> MappingLocation.of("id");
      case LOCAL_ID -> MappingLocation.of("lid");
      case ATTRIBUTE -> MappingLocation.of("attributes", property.jsonapiName());
      case RELATIONSHIP -> MappingLocation.of("relationships", property.jsonapiName(), "data");
      case RESOURCE_META -> MappingLocation.of("meta");
      case RELATIONSHIP_META -> MappingLocation.of("relationships", property.jsonapiName(), "meta");
    };
  }

  static String message(MappingProperty property, JavaType declaredType) {
    return "Mapped property '"
        + property.logicalName()
        + "' on "
        + declaredType.getRawClass().getName()
        + " has unresolved generic type information; supply a fully parameterized JavaType";
  }

  private static Iterable<MappingProperty> allProperties(ResourceMapping mapping) {
    List<MappingProperty> properties = new ArrayList<>();
    if (mapping.identifierProperty() != null) {
      properties.add(mapping.identifierProperty());
    }
    if (mapping.localIdProperty() != null) {
      properties.add(mapping.localIdProperty());
    }
    properties.addAll(mapping.attributes());
    properties.addAll(mapping.relationships());
    if (mapping.resourceMeta() != null) {
      properties.add(mapping.resourceMeta());
    }
    properties.addAll(mapping.relationshipMetaProperties());
    return properties;
  }

  private static boolean hasUnresolvedMemberType(JavaType declaredType, MappingProperty property) {
    AnnotatedMember accessor = property.accessor();
    Type memberType = genericMemberType(accessor.getMember());
    JavaType declaringType = declaredType.findSuperType(accessor.getDeclaringClass());
    return (memberType != null
            && declaringType != null
            && containsUnresolved(memberType, declaringType))
        || (memberType != null
            && property.role() == PropertyRole.RELATIONSHIP
            && hasUnresolvedRelationshipTarget(property.accessor().getType(), memberType));
  }

  private static @Nullable Type genericMemberType(Member member) {
    if (member instanceof Field field) {
      return field.getGenericType();
    }
    if (member instanceof Method method) {
      return method.getGenericReturnType();
    }
    return null;
  }

  private static boolean containsUnresolved(Type type, JavaType declaringType) {
    return switch (type) {
      case TypeVariable<?> variable -> !isBound(variable, declaringType);
      case ParameterizedType parameterized ->
          containsUnresolvedParameterizedType(parameterized, declaringType);
      case GenericArrayType array ->
          containsUnresolved(array.getGenericComponentType(), declaringType);
      case WildcardType wildcard -> containsUnresolvedWildcard(wildcard, declaringType);
      default -> false;
    };
  }

  private static boolean containsUnresolvedParameterizedType(
      ParameterizedType parameterized, JavaType declaringType) {
    if (parameterized.getOwnerType() != null
        && containsUnresolved(parameterized.getOwnerType(), declaringType)) {
      return true;
    }
    for (Type argument : parameterized.getActualTypeArguments()) {
      if (containsUnresolved(argument, declaringType)) {
        return true;
      }
    }
    return false;
  }

  private static boolean containsUnresolvedWildcard(WildcardType wildcard, JavaType declaringType) {
    for (Type upperBound : wildcard.getUpperBounds()) {
      if (containsUnresolved(upperBound, declaringType)) {
        return true;
      }
    }
    for (Type lowerBound : wildcard.getLowerBounds()) {
      if (containsUnresolved(lowerBound, declaringType)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isBound(TypeVariable<?> variable, JavaType declaringType) {
    TypeVariable<?>[] variables = declaringType.getRawClass().getTypeParameters();
    for (int i = 0; i < variables.length; i++) {
      if (variables[i].equals(variable)) {
        return declaringType.getBindings().getBoundTypeOrNull(i) != null;
      }
    }
    return false;
  }

  private static boolean hasUnresolvedRelationshipTarget(JavaType propertyType, Type memberType) {
    JavaType unwrapped = RelationshipLinkageSupport.unwrapOptionalType(propertyType);
    JavaType linkageType = RelationshipLinkageSupport.linkageJavaType(unwrapped);
    if (linkageType != null && isRawGeneric(linkageType)) {
      return true;
    }
    if (isRawGeneric(unwrapped)) {
      return true;
    }
    return containsWildcard(memberType);
  }

  private static boolean isRawGeneric(JavaType type) {
    return type.getRawClass().getTypeParameters().length > 0 && type.getBindings().isEmpty();
  }

  private static boolean containsWildcard(Type type) {
    return switch (type) {
      case WildcardType ignored -> true;
      case ParameterizedType parameterized -> {
        boolean owner =
            parameterized.getOwnerType() != null && containsWildcard(parameterized.getOwnerType());
        boolean argument = false;
        for (Type actualTypeArgument : parameterized.getActualTypeArguments()) {
          if (containsWildcard(actualTypeArgument)) {
            argument = true;
            break;
          }
        }
        yield owner || argument;
      }
      case GenericArrayType array -> containsWildcard(array.getGenericComponentType());
      default -> false;
    };
  }
}
