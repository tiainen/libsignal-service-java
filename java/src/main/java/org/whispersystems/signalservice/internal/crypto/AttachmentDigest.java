/*
 * Copyright 2023 Signal Messenger, LLC
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package org.whispersystems.signalservice.internal.crypto;

import java.util.Optional;

public record AttachmentDigest(byte[] digest, Optional<byte[]> incrementalDigest, int incrementalMacChunkSize) {}
