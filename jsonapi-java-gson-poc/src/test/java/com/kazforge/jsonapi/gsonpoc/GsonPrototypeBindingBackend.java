package com.kazforge.jsonapi.gsonpoc;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.kazforge.jsonapi.annotation.JsonApiAttribute;
import com.kazforge.jsonapi.annotation.JsonApiId;
import com.kazforge.jsonapi.annotation.JsonApiLocalId;
import com.kazforge.jsonapi.annotation.JsonApiRelationship;
import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.core.model.RelationshipData;
import com.kazforge.jsonapi.core.model.ResourceIdentifier;
import com.kazforge.jsonapi.mapping.internal.BindingDefinition;
import com.kazforge.jsonapi.mapping.internal.BindingPropertyDefinition;
import com.kazforge.jsonapi.mapping.internal.DomainBindingBackend;
import com.kazforge.jsonapi.mapping.internal.MappingRole;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Test-only Gson Core-to-application binding backend for KAZ-137. */
final class GsonPrototypeBindingBackend implements DomainBindingBackend<Type, Field> {

  private final Gson gson;

  GsonPrototypeBindingBackend(Gson gson) {
    this.gson = gson;
  }

  @Override
  public Type constructType(Class<?> rawType) {
    return rawType;
  }

  @Override
  public Class<?> rawClass(Type type) {
    return rawClassOf(type);
  }

  @Override
  public BindingDefinition<Type, Field> bindingFor(Type type) {
    Class<?> raw = rawClassOf(type);
    JsonApiResource resource = raw.getAnnotation(JsonApiResource.class);
    if (resource == null) {
      throw new IllegalArgumentException("Missing @JsonApiResource on " + raw.getName());
    }

    BindingPropertyDefinition<Type, Field> id = null;
    BindingPropertyDefinition<Type, Field> lid = null;
    List<BindingPropertyDefinition<Type, Field>> attributes = new ArrayList<>();
    List<BindingPropertyDefinition<Type, Field>> relationships = new ArrayList<>();

    for (Field field : mappedFields(raw)) {
      String externalName = externalName(field);
      Type fieldType = field.getGenericType();
      if (field.isAnnotationPresent(JsonApiId.class) || externalName.equals("id")) {
        id = property(field, externalName, "id", MappingRole.ID, fieldType);
      } else if (field.isAnnotationPresent(JsonApiLocalId.class)) {
        lid = property(field, externalName, "lid", MappingRole.LOCAL_ID, fieldType);
      } else if (field.isAnnotationPresent(JsonApiAttribute.class)) {
        attributes.add(
            property(field, externalName, externalName, MappingRole.ATTRIBUTE, fieldType));
      } else if (field.isAnnotationPresent(JsonApiRelationship.class)) {
        relationships.add(
            property(field, externalName, externalName, MappingRole.RELATIONSHIP, fieldType));
      }
    }

    return new BindingDefinition<>(
        resource.type(), type, id, lid, attributes, relationships);
  }

  @Override
  public @Nullable Object parseIdentifier(String wireIdentifier) {
    return wireIdentifier;
  }

  @Override
  public @Nullable Object convertRelationship(
      RelationshipData data, BindingPropertyDefinition<Type, Field> property) {
    if (data instanceof RelationshipData.NullLinkage) {
      return null;
    }
    if (data instanceof RelationshipData.SingleLinkage single) {
      return copyIdentifier(single.identifier());
    }
    if (data instanceof RelationshipData.IdentifierCollectionLinkage collection) {
      List<ResourceIdentifier> identifiers = new ArrayList<>(collection.identifiers().size());
      for (ResourceIdentifier identifier : collection.identifiers()) {
        identifiers.add(copyIdentifier(identifier));
      }
      return identifiers;
    }
    throw new IllegalArgumentException("Unsupported linkage: " + data);
  }

  @Override
  public Object construct(Map<String, @Nullable Object> properties, Type targetType) {
    return gson.fromJson(gson.toJsonTree(properties), targetType);
  }

  private static BindingPropertyDefinition<Type, Field> property(
      Field field,
      String externalName,
      String jsonapiName,
      MappingRole role,
      Type type) {
    return new BindingPropertyDefinition<>(
        field,
        field.getName(),
        externalName,
        jsonapiName,
        role,
        type,
        true);
  }

  private static ResourceIdentifier copyIdentifier(ResourceIdentifier identifier) {
    return new ResourceIdentifier(
        identifier.type(),
        identifier.id(),
        identifier.lid(),
        identifier.meta(),
        identifier.additionalMembers());
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
}
