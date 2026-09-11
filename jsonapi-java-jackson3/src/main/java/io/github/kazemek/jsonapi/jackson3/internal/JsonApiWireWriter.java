package io.github.kazemek.jsonapi.jackson3.internal;

import io.github.kazemek.jsonapi.core.model.Attributes;
import io.github.kazemek.jsonapi.core.model.DocumentData;
import io.github.kazemek.jsonapi.core.model.ErrorObject;
import io.github.kazemek.jsonapi.core.model.ErrorSource;
import io.github.kazemek.jsonapi.core.model.JsonApiDocument;
import io.github.kazemek.jsonapi.core.model.JsonApiMembers;
import io.github.kazemek.jsonapi.core.model.JsonApiObject;
import io.github.kazemek.jsonapi.core.model.Link;
import io.github.kazemek.jsonapi.core.model.Links;
import io.github.kazemek.jsonapi.core.model.Meta;
import io.github.kazemek.jsonapi.core.model.Relationship;
import io.github.kazemek.jsonapi.core.model.RelationshipData;
import io.github.kazemek.jsonapi.core.model.Relationships;
import io.github.kazemek.jsonapi.core.model.ResourceIdentifier;
import io.github.kazemek.jsonapi.core.model.ResourceObject;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;

/**
 * Emits JSON:API document and nested model values with deterministic member order and wire-state
 * preservation (absence vs explicit null, flat wrappers, sealed variants).
 */
final class JsonApiWireWriter {

  private JsonApiWireWriter() {}

  static void writeDocument(JsonApiDocument document, JsonGenerator gen) {
    gen.writeStartObject();
    if (document.hasDataMember()) {
      gen.writeName(JsonApiMembers.DATA);
      writeDocumentData(document.data(), gen);
    }
    if (document.hasErrorsMember()) {
      gen.writeName(JsonApiMembers.ERRORS);
      writeErrorObjects(document.errors(), gen);
    }
    Meta documentMeta = document.meta();
    if (documentMeta != null) {
      gen.writeName(JsonApiMembers.META);
      writeMeta(documentMeta, gen);
    }
    JsonApiObject jsonapi = document.jsonapi();
    if (jsonapi != null) {
      gen.writeName(JsonApiMembers.JSONAPI);
      writeJsonApiObject(jsonapi, gen);
    }
    Links documentLinks = document.links();
    if (documentLinks != null) {
      gen.writeName(JsonApiMembers.LINKS);
      writeLinks(documentLinks, gen);
    }
    if (document.hasIncludedMember()) {
      gen.writeName(JsonApiMembers.INCLUDED);
      writeResourceObjects(document.included(), gen);
    }
    writeAdditionalMembers(document.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeDocumentData(@Nullable DocumentData data, JsonGenerator gen) {
    switch (data) {
      case null -> gen.writeNull();
      case DocumentData.NullData ignored -> gen.writeNull();
      case DocumentData.SingleResource(var resource) -> writeResourceObject(resource, gen);
      case DocumentData.ResourceCollection(var resources) -> writeResourceObjects(resources, gen);
      case DocumentData.SingleIdentifier(var identifier) ->
          writeResourceIdentifier(identifier, gen);
      case DocumentData.IdentifierCollection(var identifiers) ->
          writeResourceIdentifiers(identifiers, gen);
    }
  }

  static void writeResourceObjects(@Nullable List<ResourceObject> resources, JsonGenerator gen) {
    gen.writeStartArray();
    if (resources != null) {
      for (ResourceObject resource : resources) {
        writeResourceObject(resource, gen);
      }
    }
    gen.writeEndArray();
  }

  static void writeResourceObject(ResourceObject resource, JsonGenerator gen) {
    gen.writeStartObject();
    gen.writeStringProperty(JsonApiMembers.TYPE, resource.type());
    if (resource.id() != null) {
      gen.writeStringProperty(JsonApiMembers.ID, resource.id());
    }
    if (resource.lid() != null) {
      gen.writeStringProperty(JsonApiMembers.LID, resource.lid());
    }
    Attributes attributes = resource.attributes();
    if (attributes != null) {
      gen.writeName(JsonApiMembers.ATTRIBUTES);
      writeAttributes(attributes, gen);
    }
    Relationships relationships = resource.relationships();
    if (relationships != null) {
      gen.writeName(JsonApiMembers.RELATIONSHIPS);
      writeRelationships(relationships, gen);
    }
    Links resourceLinks = resource.links();
    if (resourceLinks != null) {
      gen.writeName(JsonApiMembers.LINKS);
      writeLinks(resourceLinks, gen);
    }
    Meta resourceMeta = resource.meta();
    if (resourceMeta != null) {
      gen.writeName(JsonApiMembers.META);
      writeMeta(resourceMeta, gen);
    }
    writeAdditionalMembers(resource.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeResourceIdentifiers(List<ResourceIdentifier> identifiers, JsonGenerator gen) {
    gen.writeStartArray();
    for (ResourceIdentifier identifier : identifiers) {
      writeResourceIdentifier(identifier, gen);
    }
    gen.writeEndArray();
  }

  static void writeResourceIdentifier(ResourceIdentifier identifier, JsonGenerator gen) {
    gen.writeStartObject();
    gen.writeStringProperty(JsonApiMembers.TYPE, identifier.type());
    if (identifier.id() != null) {
      gen.writeStringProperty(JsonApiMembers.ID, identifier.id());
    }
    if (identifier.lid() != null) {
      gen.writeStringProperty(JsonApiMembers.LID, identifier.lid());
    }
    Meta identifierMeta = identifier.meta();
    if (identifierMeta != null) {
      gen.writeName(JsonApiMembers.META);
      writeMeta(identifierMeta, gen);
    }
    writeAdditionalMembers(identifier.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeAttributes(Attributes attributes, JsonGenerator gen) {
    writeOpenObject(attributes.flatten(), gen);
  }

  static void writeRelationships(Relationships relationships, JsonGenerator gen) {
    gen.writeStartObject();
    for (Map.Entry<String, @Nullable Object> entry : relationships.flatten().entrySet()) {
      gen.writeName(entry.getKey());
      Object value = entry.getValue();
      if (value instanceof Relationship relationship) {
        writeRelationship(relationship, gen);
      } else {
        writeOpenValue(value, gen);
      }
    }
    gen.writeEndObject();
  }

  static void writeRelationship(Relationship relationship, JsonGenerator gen) {
    gen.writeStartObject();
    if (relationship.hasDataMember()) {
      gen.writeName(JsonApiMembers.DATA);
      writeRelationshipData(relationship.data(), gen);
    }
    Links relationshipLinks = relationship.links();
    if (relationshipLinks != null) {
      gen.writeName(JsonApiMembers.LINKS);
      writeLinks(relationshipLinks, gen);
    }
    Meta relationshipMeta = relationship.meta();
    if (relationshipMeta != null) {
      gen.writeName(JsonApiMembers.META);
      writeMeta(relationshipMeta, gen);
    }
    writeAdditionalMembers(relationship.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeRelationshipData(@Nullable RelationshipData data, JsonGenerator gen) {
    switch (data) {
      case null -> gen.writeNull();
      case RelationshipData.NullLinkage ignored -> gen.writeNull();
      case RelationshipData.SingleLinkage(var identifier) ->
          writeResourceIdentifier(identifier, gen);
      case RelationshipData.IdentifierCollectionLinkage(var identifiers) ->
          writeResourceIdentifiers(identifiers, gen);
    }
  }

  static void writeLinks(Links links, JsonGenerator gen) {
    gen.writeStartObject();
    for (Map.Entry<String, @Nullable Object> entry : links.flatten().entrySet()) {
      gen.writeName(entry.getKey());
      switch (entry.getValue()) {
        case null -> writeLink(null, gen);
        case Link link -> writeLink(link, gen);
        case Object other -> writeOpenValue(other, gen);
      }
    }
    gen.writeEndObject();
  }

  static void writeLink(@Nullable Link link, JsonGenerator gen) {
    switch (link) {
      case null -> gen.writeNull();
      case Link.StringLink(var href) -> gen.writeString(href);
      case Link.ObjectLink objectLink -> writeObjectLink(objectLink, gen);
    }
  }

  static void writeObjectLink(Link.ObjectLink link, JsonGenerator gen) {
    gen.writeStartObject();
    gen.writeStringProperty(JsonApiMembers.HREF, link.href());
    if (link.rel() != null) {
      gen.writeStringProperty(JsonApiMembers.REL, link.rel());
    }
    if (link.describedby() != null) {
      gen.writeName(JsonApiMembers.DESCRIBEDBY);
      writeLink(link.describedby(), gen);
    }
    if (link.title() != null) {
      gen.writeStringProperty(JsonApiMembers.TITLE, link.title());
    }
    if (link.type() != null) {
      gen.writeStringProperty(JsonApiMembers.TYPE, link.type());
    }
    if (link.hreflang() != null) {
      gen.writeName(JsonApiMembers.HREFLANG);
      gen.writeStartArray();
      for (String tag : link.hreflang()) {
        gen.writeString(tag);
      }
      gen.writeEndArray();
    }
    Meta linkMeta = link.meta();
    if (linkMeta != null) {
      gen.writeName(JsonApiMembers.META);
      writeMeta(linkMeta, gen);
    }
    writeAdditionalMembers(link.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeMeta(Meta meta, JsonGenerator gen) {
    writeOpenObject(meta.members(), gen);
  }

  static void writeJsonApiObject(JsonApiObject jsonapi, JsonGenerator gen) {
    gen.writeStartObject();
    if (jsonapi.version() != null) {
      gen.writeStringProperty(JsonApiMembers.VERSION, jsonapi.version());
    }
    List<String> ext = jsonapi.ext();
    if (ext != null) {
      gen.writeName(JsonApiMembers.EXT);
      writeStringArray(ext, gen);
    }
    List<String> profile = jsonapi.profile();
    if (profile != null) {
      gen.writeName(JsonApiMembers.PROFILE);
      writeStringArray(profile, gen);
    }
    Meta jsonapiMeta = jsonapi.meta();
    if (jsonapiMeta != null) {
      gen.writeName(JsonApiMembers.META);
      writeMeta(jsonapiMeta, gen);
    }
    writeAdditionalMembers(jsonapi.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeErrorObjects(@Nullable List<ErrorObject> errors, JsonGenerator gen) {
    gen.writeStartArray();
    if (errors != null) {
      for (ErrorObject error : errors) {
        writeErrorObject(error, gen);
      }
    }
    gen.writeEndArray();
  }

  static void writeErrorObject(ErrorObject error, JsonGenerator gen) {
    gen.writeStartObject();
    if (error.id() != null) {
      gen.writeStringProperty(JsonApiMembers.ID, error.id());
    }
    Links errorLinks = error.links();
    if (errorLinks != null) {
      gen.writeName(JsonApiMembers.LINKS);
      writeLinks(errorLinks, gen);
    }
    if (error.status() != null) {
      gen.writeStringProperty(JsonApiMembers.STATUS, error.status());
    }
    if (error.code() != null) {
      gen.writeStringProperty(JsonApiMembers.CODE, error.code());
    }
    if (error.title() != null) {
      gen.writeStringProperty(JsonApiMembers.TITLE, error.title());
    }
    if (error.detail() != null) {
      gen.writeStringProperty(JsonApiMembers.DETAIL, error.detail());
    }
    ErrorSource source = error.source();
    if (source != null) {
      gen.writeName(JsonApiMembers.SOURCE);
      writeErrorSource(source, gen);
    }
    Meta errorMeta = error.meta();
    if (errorMeta != null) {
      gen.writeName(JsonApiMembers.META);
      writeMeta(errorMeta, gen);
    }
    writeAdditionalMembers(error.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeErrorSource(ErrorSource source, JsonGenerator gen) {
    gen.writeStartObject();
    if (source.pointer() != null) {
      gen.writeStringProperty(JsonApiMembers.POINTER, source.pointer());
    }
    if (source.parameter() != null) {
      gen.writeStringProperty(JsonApiMembers.PARAMETER, source.parameter());
    }
    if (source.header() != null) {
      gen.writeStringProperty(JsonApiMembers.HEADER, source.header());
    }
    writeAdditionalMembers(source.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeAdditionalMembers(Map<String, @Nullable Object> members, JsonGenerator gen) {
    for (Map.Entry<String, @Nullable Object> entry : members.entrySet()) {
      gen.writeName(entry.getKey());
      writeOpenValue(entry.getValue(), gen);
    }
  }

  static void writeOpenObject(Map<String, @Nullable Object> members, JsonGenerator gen) {
    gen.writeStartObject();
    for (Map.Entry<String, @Nullable Object> entry : members.entrySet()) {
      gen.writeName(entry.getKey());
      writeOpenValue(entry.getValue(), gen);
    }
    gen.writeEndObject();
  }

  static void writeOpenValue(@Nullable Object value, JsonGenerator gen) {
    switch (value) {
      case null -> gen.writeNull();
      case String s -> gen.writeString(s);
      case Boolean b -> gen.writeBoolean(b);
      case Number n -> writeNumber(n, gen);
      case List<?> list -> {
        gen.writeStartArray();
        for (Object element : list) {
          writeOpenValue(element, gen);
        }
        gen.writeEndArray();
      }
      case Map<?, ?> map -> {
        gen.writeStartObject();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
          gen.writeName(String.valueOf(entry.getKey()));
          writeOpenValue(entry.getValue(), gen);
        }
        gen.writeEndObject();
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported open JSON value type: " + value.getClass().getName());
    }
  }

  private static void writeNumber(Number number, JsonGenerator gen) {
    switch (number) {
      case BigDecimal bigDecimal -> gen.writeNumber(bigDecimal);
      case BigInteger bigInteger -> gen.writeNumber(bigInteger);
      case Double d -> gen.writeNumber(d);
      case Float f -> gen.writeNumber(f);
      case Long l -> gen.writeNumber(l);
      case Integer i -> gen.writeNumber(i);
      case Short s -> gen.writeNumber(s);
      case Byte b -> gen.writeNumber(b.shortValue());
      default -> gen.writeNumber(number.toString());
    }
  }

  private static void writeStringArray(List<String> values, JsonGenerator gen) {
    gen.writeStartArray();
    for (String value : values) {
      gen.writeString(value);
    }
    gen.writeEndArray();
  }
}
