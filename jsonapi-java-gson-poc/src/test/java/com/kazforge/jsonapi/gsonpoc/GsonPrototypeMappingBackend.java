package com.kazforge.jsonapi.gsonpoc;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.mapping.internal.DomainMappingBackend;
import com.kazforge.jsonapi.mapping.internal.MappingDefinition;
import com.kazforge.jsonapi.mapping.internal.MappingPropertyDefinition;
import com.kazforge.jsonapi.mapping.internal.MappingRole;
import com.kazforge.jsonapi.mapping.internal.MappingValue;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Test-only Gson backend for the KAZ-137 mapping-domain fitness test. */
final class GsonPrototypeMappingBackend implements DomainMappingBackend<Type, Field> {

  private final Gson gson;

  GsonPrototypeMappingBackend(Gson gson) {
    this.gson = gson;
  }

  @Override
  public Type inferredType(Object domain) {
    return domain.getClass();
  }

  @Override
  public Type effectiveType(Object domain, Type declaredType) {
    Class<?> raw = rawClass(declaredType);
    return raw == domain.getClass() ? declaredType : domain.getClass();
  }

  @Override
  public Class<?> rawClass(Type type) {
    return rawClassOf(type);
  }

  @Override
  public MappingDefinition<Type, Field> mappingFor(Type type) {
    Class<?> raw = rawClassOf(type);
    JsonApiResource resource = raw.getAnnotation(JsonApiResource.class);
    if (resource == null) {
      throw new IllegalArgumentException("Missing @JsonApiResource on " + raw.getName());
    }

    MappingPropertyDefinition<Type, Field> id = null;
    MappingPropertyDefinition<Type, Field> lid = null;
    List<MappingPropertyDefinition<Type, Field>> attributes = new ArrayList<>();
    List<MappingPropertyDefinition<Type, Field>> relationships = new ArrayList<>();

    for (Field field : mappedFields(raw)) {
      String externalName = externalName(field);
      Type fieldType = field.getGenericType();
      if (field.isAnnotationPresent(JsonApiId.class) || externalName.equals("id")) {
        id = property(field, externalName, MappingRole.ID, fieldType, false);
      } else if (field.isAnnotationPresent(JsonApiLocalId.class)) {
        lid = property(field, externalName, MappingRole.LOCAL_ID, fieldType, false);
      } else if (field.isAnnotationPresent(JsonApiAttribute.class)) {
        attributes.add(property(field, externalName, MappingRole.ATTRIBUTE, fieldType, false));
      } else if (field.isAnnotationPresent(JsonApiRelationship.class)) {
        relationships.add(
            property(field, externalName, MappingRole.RELATIONSHIP, fieldType, isToMany(fieldType)));
      }
    }

    return new MappingDefinition<>(
        resource.type(), type, id, lid, attributes, relationships);
  }

  @Override
  public @Nullable Object read(
      Object domain, MappingPropertyDefinition<Type, Field> property) {
    Field field = property.handle();
    try {
      if (!field.canAccess(domain) && !field.trySetAccessible()) {
        throw new IllegalArgumentException("Cannot access " + field);
      }
      return field.get(domain);
    } catch (IllegalAccessException ex) {
      throw new IllegalArgumentException("Cannot access " + field, ex);
    }
  }

  @Override
  public MappingValue convertAttribute(
      Object domain,
      MappingDefinition<Type, Field> mapping,
      MappingPropertyDefinition<Type, Field> property) {
    return MappingValue.emitted(toOpenValue(gson.toJsonTree(read(domain, property))));
  }

  @Override
  public @Nullable String convertIdentifier(@Nullable Object value) {
    return value == null ? null : value.toString();
  }

  @Override
  public Type relationshipTargetType(MappingPropertyDefinition<Type, Field> property) {
    Type type = property.declaredType();
    if (type instanceof ParameterizedType parameterized
        && Collection.class.isAssignableFrom(rawClassOf(parameterized.getRawType()))) {
      Type[] arguments = parameterized.getActualTypeArguments();
      if (arguments.length == 1) {
        return arguments[0];
      }
    }
    if (type instanceof Class<?> cls && cls.isArray()) {
      return cls.getComponentType();
    }
    return type;
  }

  @Override
  public List<Object> relationshipValues(
      @Nullable Object rawValue, MappingPropertyDefinition<Type, Field> property) {
    if (rawValue == null) {
      return List.of();
    }
    if (!property.toMany()) {
      return List.of(rawValue);
    }
    if (rawValue instanceof Collection<?> collection) {
      return new ArrayList<>(collection);
    }
    if (rawValue.getClass().isArray()) {
      int length = Array.getLength(rawValue);
      List<Object> values = new ArrayList<>(length);
      for (int i = 0; i < length; i++) {
        values.add(Array.get(rawValue, i));
      }
      return values;
    }
    throw new IllegalArgumentException("Expected collection/array but got " + rawValue.getClass());
  }

  private static MappingPropertyDefinition<Type, Field> property(
      Field field, String externalName, MappingRole role, Type type, boolean toMany) {
    String jsonapiName =
        switch (role) {
          case ID -> "id";
          case LOCAL_ID -> "lid";
          case ATTRIBUTE, RELATIONSHIP -> externalName;
        };
    return new MappingPropertyDefinition<>(
        field, field.getName(), externalName, jsonapiName, role, type, toMany);
  }

  private static boolean isToMany(Type type) {
    Class<?> raw = rawClassOf(type);
    return raw.isArray() || Collection.class.isAssignableFrom(raw);
  }

  private static List<Field> mappedFields(Class<?> type) {
    List<Field> fields = new ArrayList<>();
    for (Class<?> current = type;
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
          fields.add(field);
        }
      }
    }
    return fields;
  }

  private static String externalName(Field field) {
    SerializedName serializedName = field.getAnnotation(SerializedName.class);
    return serializedName != null ? serializedName.value() : field.getName();
  }

  private static Class<?> rawClassOf(Type type) {
    if (type instanceof Class<?> cls) {
      return cls;
    }
    if (type instanceof ParameterizedType parameterized
        && parameterized.getRawType() instanceof Class<?> cls) {
      return cls;
    }
    throw new IllegalArgumentException("Unsupported PoC type: " + type);
  }

  private static @Nullable Object toOpenValue(JsonElement element) {
    if (element == null || element instanceof JsonNull) {
      return null;
    }
    if (element instanceof JsonPrimitive primitive) {
      if (primitive.isBoolean()) {
        return primitive.getAsBoolean();
      }
      if (primitive.isNumber()) {
        return primitive.getAsNumber();
      }
      return primitive.getAsString();
    }
    if (element instanceof JsonArray array) {
      List<Object> values = new ArrayList<>(array.size());
      for (JsonElement item : array) {
        values.add(toOpenValue(item));
      }
      return java.util.Collections.unmodifiableList(values);
    }
    if (element instanceof JsonObject object) {
      Map<String, Object> values = new java.util.LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
        values.put(entry.getKey(), toOpenValue(entry.getValue()));
      }
      return java.util.Collections.unmodifiableMap(values);
    }
    throw new IllegalArgumentException("Unsupported Gson value: " + element);
  }
}
