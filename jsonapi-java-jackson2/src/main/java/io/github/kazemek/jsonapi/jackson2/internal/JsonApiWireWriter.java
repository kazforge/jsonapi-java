package io.github.kazemek.jsonapi.jackson2.internal;

import com.fasterxml.jackson.core.JsonGenerator;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Emits JSON:API document and nested model values with deterministic member order and wire-state
 * preservation (absence vs explicit null, flat wrappers, sealed variants). Adapted to Jackson 2
 * generator mechanics ({@code writeFieldName}, no string-property conveniences, checked {@code
 * IOException}).
 */
final class JsonApiWireWriter {

  private JsonApiWireWriter() {}

  static void writeDocument(JsonApiDocument document, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    if (document.hasDataMember()) {
      gen.writeFieldName(JsonApiMembers.DATA);
      writeDocumentData(document.data(), gen);
    }
    if (document.hasErrorsMember()) {
      gen.writeFieldName(JsonApiMembers.ERRORS);
      writeErrorObjects(document.errors(), gen);
    }
    Meta documentMeta = document.meta();
    if (documentMeta != null) {
      gen.writeFieldName(JsonApiMembers.META);
      writeMeta(documentMeta, gen);
    }
    JsonApiObject jsonapi = document.jsonapi();
    if (jsonapi != null) {
      gen.writeFieldName(JsonApiMembers.JSONAPI);
      writeJsonApiObject(jsonapi, gen);
    }
    Links documentLinks = document.links();
    if (documentLinks != null) {
      gen.writeFieldName(JsonApiMembers.LINKS);
      writeLinks(documentLinks, gen);
    }
    if (document.hasIncludedMember()) {
      gen.writeFieldName(JsonApiMembers.INCLUDED);
      writeResourceObjects(document.included(), gen);
    }
    writeAdditionalMembers(document.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeDocumentData(@Nullable DocumentData data, JsonGenerator gen) throws IOException {
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

  static void writeResourceObjects(@Nullable List<ResourceObject> resources, JsonGenerator gen)
      throws IOException {
    gen.writeStartArray();
    if (resources != null) {
      for (ResourceObject resource : resources) {
        writeResourceObject(resource, gen);
      }
    }
    gen.writeEndArray();
  }

  static void writeResourceObject(ResourceObject resource, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    writeStringMember(gen, JsonApiMembers.TYPE, resource.type());
    if (resource.id() != null) {
      writeStringMember(gen, JsonApiMembers.ID, resource.id());
    }
    if (resource.lid() != null) {
      writeStringMember(gen, JsonApiMembers.LID, resource.lid());
    }
    Attributes attributes = resource.attributes();
    if (attributes != null) {
      gen.writeFieldName(JsonApiMembers.ATTRIBUTES);
      writeAttributes(attributes, gen);
    }
    Relationships relationships = resource.relationships();
    if (relationships != null) {
      gen.writeFieldName(JsonApiMembers.RELATIONSHIPS);
      writeRelationships(relationships, gen);
    }
    Links resourceLinks = resource.links();
    if (resourceLinks != null) {
      gen.writeFieldName(JsonApiMembers.LINKS);
      writeLinks(resourceLinks, gen);
    }
    Meta resourceMeta = resource.meta();
    if (resourceMeta != null) {
      gen.writeFieldName(JsonApiMembers.META);
      writeMeta(resourceMeta, gen);
    }
    writeAdditionalMembers(resource.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeResourceIdentifiers(List<ResourceIdentifier> identifiers, JsonGenerator gen)
      throws IOException {
    gen.writeStartArray();
    for (ResourceIdentifier identifier : identifiers) {
      writeResourceIdentifier(identifier, gen);
    }
    gen.writeEndArray();
  }

  static void writeResourceIdentifier(ResourceIdentifier identifier, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();
    writeStringMember(gen, JsonApiMembers.TYPE, identifier.type());
    if (identifier.id() != null) {
      writeStringMember(gen, JsonApiMembers.ID, identifier.id());
    }
    if (identifier.lid() != null) {
      writeStringMember(gen, JsonApiMembers.LID, identifier.lid());
    }
    Meta identifierMeta = identifier.meta();
    if (identifierMeta != null) {
      gen.writeFieldName(JsonApiMembers.META);
      writeMeta(identifierMeta, gen);
    }
    writeAdditionalMembers(identifier.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeAttributes(Attributes attributes, JsonGenerator gen) throws IOException {
    writeOpenObject(attributes.flatten(), gen);
  }

  static void writeRelationships(Relationships relationships, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();
    for (Map.Entry<String, @Nullable Object> entry : relationships.flatten().entrySet()) {
      gen.writeFieldName(entry.getKey());
      Object value = entry.getValue();
      if (value instanceof Relationship relationship) {
        writeRelationship(relationship, gen);
      } else {
        writeOpenValue(value, gen);
      }
    }
    gen.writeEndObject();
  }

  static void writeRelationship(Relationship relationship, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    if (relationship.hasDataMember()) {
      gen.writeFieldName(JsonApiMembers.DATA);
      writeRelationshipData(relationship.data(), gen);
    }
    Links relationshipLinks = relationship.links();
    if (relationshipLinks != null) {
      gen.writeFieldName(JsonApiMembers.LINKS);
      writeLinks(relationshipLinks, gen);
    }
    Meta relationshipMeta = relationship.meta();
    if (relationshipMeta != null) {
      gen.writeFieldName(JsonApiMembers.META);
      writeMeta(relationshipMeta, gen);
    }
    writeAdditionalMembers(relationship.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeRelationshipData(@Nullable RelationshipData data, JsonGenerator gen)
      throws IOException {
    switch (data) {
      case null -> gen.writeNull();
      case RelationshipData.NullLinkage ignored -> gen.writeNull();
      case RelationshipData.SingleLinkage(var identifier) ->
          writeResourceIdentifier(identifier, gen);
      case RelationshipData.IdentifierCollectionLinkage(var identifiers) ->
          writeResourceIdentifiers(identifiers, gen);
    }
  }

  static void writeLinks(Links links, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    for (Map.Entry<String, @Nullable Object> entry : links.flatten().entrySet()) {
      gen.writeFieldName(entry.getKey());
      switch (entry.getValue()) {
        case null -> writeLink(null, gen);
        case Link link -> writeLink(link, gen);
        case Object other -> writeOpenValue(other, gen);
      }
    }
    gen.writeEndObject();
  }

  static void writeLink(@Nullable Link link, JsonGenerator gen) throws IOException {
    switch (link) {
      case null -> gen.writeNull();
      case Link.StringLink(var href) -> gen.writeString(href);
      case Link.ObjectLink objectLink -> writeObjectLink(objectLink, gen);
    }
  }

  static void writeObjectLink(Link.ObjectLink link, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    writeStringMember(gen, JsonApiMembers.HREF, link.href());
    if (link.rel() != null) {
      writeStringMember(gen, JsonApiMembers.REL, link.rel());
    }
    if (link.describedby() != null) {
      writeStringMember(gen, JsonApiMembers.DESCRIBEDBY, link.describedby());
    }
    if (link.title() != null) {
      writeStringMember(gen, JsonApiMembers.TITLE, link.title());
    }
    if (link.type() != null) {
      writeStringMember(gen, JsonApiMembers.TYPE, link.type());
    }
    if (link.hreflang() != null) {
      gen.writeFieldName(JsonApiMembers.HREFLANG);
      gen.writeStartArray();
      for (String tag : link.hreflang()) {
        gen.writeString(tag);
      }
      gen.writeEndArray();
    }
    Meta linkMeta = link.meta();
    if (linkMeta != null) {
      gen.writeFieldName(JsonApiMembers.META);
      writeMeta(linkMeta, gen);
    }
    writeAdditionalMembers(link.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeMeta(Meta meta, JsonGenerator gen) throws IOException {
    writeOpenObject(meta.members(), gen);
  }

  static void writeJsonApiObject(JsonApiObject jsonapi, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    if (jsonapi.version() != null) {
      writeStringMember(gen, JsonApiMembers.VERSION, jsonapi.version());
    }
    List<String> ext = jsonapi.ext();
    if (ext != null) {
      gen.writeFieldName(JsonApiMembers.EXT);
      writeStringArray(ext, gen);
    }
    List<String> profile = jsonapi.profile();
    if (profile != null) {
      gen.writeFieldName(JsonApiMembers.PROFILE);
      writeStringArray(profile, gen);
    }
    Meta jsonapiMeta = jsonapi.meta();
    if (jsonapiMeta != null) {
      gen.writeFieldName(JsonApiMembers.META);
      writeMeta(jsonapiMeta, gen);
    }
    writeAdditionalMembers(jsonapi.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeErrorObjects(@Nullable List<ErrorObject> errors, JsonGenerator gen)
      throws IOException {
    gen.writeStartArray();
    if (errors != null) {
      for (ErrorObject error : errors) {
        writeErrorObject(error, gen);
      }
    }
    gen.writeEndArray();
  }

  static void writeErrorObject(ErrorObject error, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    if (error.id() != null) {
      writeStringMember(gen, JsonApiMembers.ID, error.id());
    }
    Links errorLinks = error.links();
    if (errorLinks != null) {
      gen.writeFieldName(JsonApiMembers.LINKS);
      writeLinks(errorLinks, gen);
    }
    if (error.status() != null) {
      writeStringMember(gen, JsonApiMembers.STATUS, error.status());
    }
    if (error.code() != null) {
      writeStringMember(gen, JsonApiMembers.CODE, error.code());
    }
    if (error.title() != null) {
      writeStringMember(gen, JsonApiMembers.TITLE, error.title());
    }
    if (error.detail() != null) {
      writeStringMember(gen, JsonApiMembers.DETAIL, error.detail());
    }
    ErrorSource source = error.source();
    if (source != null) {
      gen.writeFieldName(JsonApiMembers.SOURCE);
      writeErrorSource(source, gen);
    }
    Meta errorMeta = error.meta();
    if (errorMeta != null) {
      gen.writeFieldName(JsonApiMembers.META);
      writeMeta(errorMeta, gen);
    }
    writeAdditionalMembers(error.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeErrorSource(ErrorSource source, JsonGenerator gen) throws IOException {
    gen.writeStartObject();
    if (source.pointer() != null) {
      writeStringMember(gen, JsonApiMembers.POINTER, source.pointer());
    }
    if (source.parameter() != null) {
      writeStringMember(gen, JsonApiMembers.PARAMETER, source.parameter());
    }
    if (source.header() != null) {
      writeStringMember(gen, JsonApiMembers.HEADER, source.header());
    }
    writeAdditionalMembers(source.additionalMembers(), gen);
    gen.writeEndObject();
  }

  static void writeAdditionalMembers(Map<String, @Nullable Object> members, JsonGenerator gen)
      throws IOException {
    for (Map.Entry<String, @Nullable Object> entry : members.entrySet()) {
      gen.writeFieldName(entry.getKey());
      writeOpenValue(entry.getValue(), gen);
    }
  }

  static void writeOpenObject(Map<String, @Nullable Object> members, JsonGenerator gen)
      throws IOException {
    gen.writeStartObject();
    for (Map.Entry<String, @Nullable Object> entry : members.entrySet()) {
      gen.writeFieldName(entry.getKey());
      writeOpenValue(entry.getValue(), gen);
    }
    gen.writeEndObject();
  }

  static void writeOpenValue(@Nullable Object value, JsonGenerator gen) throws IOException {
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
          gen.writeFieldName(String.valueOf(entry.getKey()));
          writeOpenValue(entry.getValue(), gen);
        }
        gen.writeEndObject();
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported open JSON value type: " + value.getClass().getName());
    }
  }

  /**
   * Jackson 2 replacement for Jackson 3's string-property generator convenience: write the member
   * name, then the string value.
   */
  private static void writeStringMember(JsonGenerator gen, String name, String value)
      throws IOException {
    gen.writeFieldName(name);
    gen.writeString(value);
  }

  private static void writeNumber(Number number, JsonGenerator gen) throws IOException {
    switch (number) {
      case BigDecimal bigDecimal -> gen.writeNumber(bigDecimal);
      case BigInteger bigInteger -> gen.writeNumber(bigInteger);
      case Double d -> gen.writeNumber(d);
      case Float f -> gen.writeNumber(f);
      case Long l -> gen.writeNumber(l);
      case Integer i -> gen.writeNumber(i);
      case Short s -> gen.writeNumber(s.intValue());
      case Byte b -> gen.writeNumber(b.intValue());
      default -> gen.writeNumber(number.toString());
    }
  }

  private static void writeStringArray(List<String> values, JsonGenerator gen) throws IOException {
    gen.writeStartArray();
    for (String value : values) {
      gen.writeString(value);
    }
    gen.writeEndArray();
  }
}
