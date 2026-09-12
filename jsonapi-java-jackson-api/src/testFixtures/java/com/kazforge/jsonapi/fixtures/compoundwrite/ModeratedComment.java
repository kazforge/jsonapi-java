package com.kazforge.jsonapi.fixtures.compoundwrite;

import com.kazforge.jsonapi.annotation.JsonApiResource;
import com.kazforge.jsonapi.fixtures.domainwrite.Person;
import org.jspecify.annotations.Nullable;

/** Runtime subtype whose JSON:API type differs from {@link BaseComment}. */
@JsonApiResource(type = "moderated-comments")
public final class ModeratedComment extends BaseComment {

  public ModeratedComment() {
    super();
  }

  public ModeratedComment(@Nullable String id, @Nullable String body, @Nullable Person author) {
    super(id, body, author);
  }
}
