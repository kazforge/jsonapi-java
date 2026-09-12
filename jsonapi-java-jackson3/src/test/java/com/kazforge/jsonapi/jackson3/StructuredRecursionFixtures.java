package com.kazforge.jsonapi.jackson3;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;
import java.util.Objects;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * Structured domain value types that probe how recursive structured binding honors Jackson
 * property-scoped authority: property-level deserializers, setter-level type refinement, null
 * providers, and polymorphic property TypeDeserializers. Owned by {@code
 * PatchStructuredBindingSpec} and the internal {@code StructuredValueBinderSpec} engine tests
 * (ADR-014).
 */
@SuppressWarnings({"unused", "NullAway"})
public final class StructuredRecursionFixtures {

  private StructuredRecursionFixtures() {}

  /**
   * Test deserializer that uppercases a string. Used on the low-level path to prove nested atomic
   * conversion preserves property-scoped {@code @JsonDeserialize} authority, and on presence-aware
   * PATCH shapes to mark a member as wrapper-customized and thus invalid on the typed path.
   */
  public static final class UpperCaseStringDeserializer extends StdDeserializer<String> {

    public UpperCaseStringDeserializer() {
      super(String.class);
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context) {
      return parser.getValueAsString().toUpperCase(java.util.Locale.ROOT);
    }
  }

  /**
   * Ordinary non-record structured domain value type with a property-level {@code @JsonDeserialize}
   * on a JavaBean field. Proves the low-level path treats such a member as atomic (rather than
   * traversing it) while still honoring the property-scoped deserializer during nested atomic
   * conversion (ADR-014).
   */
  public static final class AddressWithLoudNote {

    private String street;

    @JsonDeserialize(using = UpperCaseStringDeserializer.class)
    private String note;

    public AddressWithLoudNote() {}

    public AddressWithLoudNote(String street, String note) {
      this.street = street;
      this.note = note;
    }

    public String getStreet() {
      return street;
    }

    public void setStreet(String street) {
      this.street = street;
    }

    public String getNote() {
      return note;
    }

    public void setNote(String note) {
      this.note = note;
    }
  }

  /**
   * Ordinary traversable structured domain value type used by the low-level custom-deserializer
   * boundary fixtures (ADR-014).
   */
  public record Details(String name) {}

  /**
   * Property-scoped {@code @JsonDeserialize} for {@link Details} that returns a known value
   * regardless of the wire content, proving a customized bean-valued nested member is honored
   * atomically rather than recursed into a {@code StructuredPatch} on the low-level path (ADR-014).
   */
  public static final class CustomDetailsDeserializer extends StdDeserializer<Details> {

    public CustomDetailsDeserializer() {
      super(Details.class);
    }

    @Override
    public Details deserialize(JsonParser parser, DeserializationContext context) {
      return new Details("custom");
    }
  }

  /**
   * Ordinary structured domain value type with a bean-valued {@code details} property whose setter
   * carries a property-scoped {@code @JsonDeserialize}. The surrounding bean recurses while the
   * customized {@code details} member stays Atomic with the custom deserializer applied (ADR-014).
   */
  public static final class OuterWithSetterCustomDetails {

    private Details details;

    public OuterWithSetterCustomDetails() {}

    public OuterWithSetterCustomDetails(Details details) {
      this.details = details;
    }

    public Details getDetails() {
      return details;
    }

    @JsonDeserialize(using = CustomDetailsDeserializer.class)
    public void setDetails(Details details) {
      this.details = details;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof OuterWithSetterCustomDetails that)) {
        return false;
      }
      return Objects.equals(details, that.details);
    }

    @Override
    public int hashCode() {
      return Objects.hash(details);
    }
  }

  /**
   * Ordinary structured domain value type (record / creator-bound) with a bean-valued {@code
   * details} creator parameter carrying a property-scoped {@code @JsonDeserialize}. The customized
   * {@code details} member stays Atomic with the custom deserializer applied (ADR-014).
   */
  public record OuterWithCreatorCustomDetails(
      @JsonDeserialize(using = CustomDetailsDeserializer.class) Details details) {}

  /**
   * Ordinary traversable structured domain value supertype used by the setter-level
   * {@code @JsonDeserialize(as = ...)} low-level fixtures (ADR-014). Without the {@code as}
   * refinement this concrete bean would recurse on the low-level path.
   */
  public static class BaseProfile {

    private String name;

    public BaseProfile() {}

    public BaseProfile(String name) {
      this.name = name;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof BaseProfile that)) {
        return false;
      }
      return Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
      return Objects.hash(name);
    }
  }

  /**
   * Concrete subtype of {@link BaseProfile} used as the {@code @JsonDeserialize(as = ...)} target
   * on the setter of a {@link BaseProfile}-typed member (ADR-014).
   */
  public static final class ExtendedProfile extends BaseProfile {

    private String email;

    public ExtendedProfile() {}

    public ExtendedProfile(String name, String email) {
      super(name);
      this.email = email;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    @Override
    public boolean equals(Object other) {
      if (!(other instanceof ExtendedProfile that) || !super.equals(other)) {
        return false;
      }
      return Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
      return Objects.hash(super.hashCode(), email);
    }
  }

  /**
   * Ordinary structured domain value type with a {@link BaseProfile}-typed {@code profile} member
   * whose setter carries {@code @JsonDeserialize(as = ExtendedProfile.class)}. The refinement is a
   * type-refinement customization that must be detected through the resolved property type (a
   * setter {@code AnnotatedMethod.getType()} is {@code void}, which makes refinement checks against
   * it incorrect), keeping the member Atomic with the refined deserializer applied instead of
   * recursing (ADR-014).
   */
  public static final class OuterWithSetterAsProfile {

    private BaseProfile profile;

    public OuterWithSetterAsProfile() {}

    public OuterWithSetterAsProfile(BaseProfile profile) {
      this.profile = profile;
    }

    public BaseProfile getProfile() {
      return profile;
    }

    @JsonDeserialize(as = ExtendedProfile.class)
    public void setProfile(BaseProfile profile) {
      this.profile = profile;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof OuterWithSetterAsProfile that)) {
        return false;
      }
      return Objects.equals(profile, that.profile);
    }

    @Override
    public int hashCode() {
      return Objects.hash(profile);
    }
  }

  /**
   * Ordinary structured domain value type whose {@code city} setter carries
   * {@code @JsonSetter(nulls = Nulls.AS_EMPTY)}, proving nested explicit null converts through the
   * containing property's null provider ({@code ""}) rather than the root target deserializer's
   * null value ({@code null}) on the low-level path (ADR-014).
   */
  public static final class OuterWithNullEmptyCity {

    private String city;

    public OuterWithNullEmptyCity() {}

    public OuterWithNullEmptyCity(String city) {
      this.city = city;
    }

    public String getCity() {
      return city;
    }

    @JsonSetter(nulls = Nulls.AS_EMPTY)
    public void setCity(String city) {
      this.city = city;
    }
  }

  /**
   * Abstract polymorphic value type used by the low-level atomic-conversion fixture, proving a
   * property-level {@code TypeDeserializer} is preserved through the containing {@code
   * SettableBeanProperty.deserialize} (ADR-014).
   */
  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
  @JsonSubTypes({@JsonSubTypes.Type(value = EmailContact.class, name = "email")})
  public abstract static class Contact {}

  /**
   * Concrete {@link Contact} subtype for the polymorphic low-level atomic-conversion fixture
   * (ADR-014).
   */
  public static final class EmailContact extends Contact {

    private String email;

    public EmailContact() {}

    public EmailContact(String email) {
      this.email = email;
    }

    public String getEmail() {
      return email;
    }

    public void setEmail(String email) {
      this.email = email;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof EmailContact that)) {
        return false;
      }
      return Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
      return Objects.hash(email);
    }
  }

  /**
   * Ordinary structured domain value type with a polymorphic {@link Contact} member, proving a
   * property-level {@code TypeDeserializer} path is preserved through the low-level atomic
   * conversion ({@code SettableBeanProperty.deserialize}) (ADR-014).
   */
  public static final class OuterWithTypedContact {

    private Contact contact;

    public OuterWithTypedContact() {}

    public OuterWithTypedContact(Contact contact) {
      this.contact = contact;
    }

    public Contact getContact() {
      return contact;
    }

    public void setContact(Contact contact) {
      this.contact = contact;
    }

    @Override
    public boolean equals(Object other) {
      if (this == other) {
        return true;
      }
      if (!(other instanceof OuterWithTypedContact that)) {
        return false;
      }
      return Objects.equals(contact, that.contact);
    }

    @Override
    public int hashCode() {
      return Objects.hash(contact);
    }
  }
}
