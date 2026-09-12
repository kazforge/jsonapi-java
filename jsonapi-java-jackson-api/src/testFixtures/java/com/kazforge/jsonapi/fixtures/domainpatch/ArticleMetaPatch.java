package com.kazforge.jsonapi.fixtures.domainpatch;

import com.kazforge.jsonapi.jackson.patch.PatchPresence;

/** Presence-aware nested PATCH shape for resource meta (ADR-014/015 recursion). */
public record ArticleMetaPatch(PatchPresence<String> source, PatchPresence<String> note) {}
