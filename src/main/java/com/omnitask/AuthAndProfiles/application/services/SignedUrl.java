package com.omnitask.AuthAndProfiles.application.services;

import java.time.Instant;

/** URL temporal de solo lectura (SAS) y el momento en que expira. */
public record SignedUrl(String url, Instant expiresAt) {
}
