/**
 * Cross-artifact mapping implementation detail shared by backend runtimes.
 *
 * <p>This package is not consumer SPI. Its Java-public types exist only so backend artifacts can
 * cooperate on neutral mapping implementation once extraction moves helpers here. Application code
 * must not depend on it, and backend supported public signatures must not expose it.
 */
@NullMarked
package com.kazforge.jsonapi.mapping.internal;

import org.jspecify.annotations.NullMarked;
