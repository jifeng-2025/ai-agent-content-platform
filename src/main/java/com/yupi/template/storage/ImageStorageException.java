package com.yupi.template.storage;
public class ImageStorageException extends RuntimeException { public ImageStorageException(){super("STORAGE_FAILED: generated image retained where possible; do not regenerate automatically");} }
