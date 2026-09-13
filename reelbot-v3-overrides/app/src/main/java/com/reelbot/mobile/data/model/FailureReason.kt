package com.reelbot.mobile.data.model

/**
 * Every failure surfaced to the user maps to one of these. Nothing in this app is allowed
 * to swallow an error and substitute placeholder/demo output instead — if a step fails,
 * the job moves to JobStatus.FAILED carrying one of these reasons plus the raw underlying
 * message, and the UI shows it verbatim.
 */
enum class FailureReason(val userMessage: String) {
    MODEL_NOT_INSTALLED("The on-device speech model isn't installed yet."),
    MODEL_VERIFICATION_FAILED("The downloaded speech model failed verification and was discarded."),
    TRANSCRIPTION_FAILED("Transcription failed."),
    NO_SPEECH_DETECTED("No speech was detected in this video."),
    NO_HIGHLIGHTS("No coherent speech highlight of at least 15 seconds was found."),
    NO_AUDIO_TRACK("This video has no audio track."),
    UNSUPPORTED_CODEC("This video's format isn't supported for processing."),
    INSUFFICIENT_STORAGE("Not enough free storage to process this video."),
    RENDER_FAILED("Rendering the Reel failed."),
    FACE_DETECTOR_FAILED("Face detection failed; falling back to center-crop was also unsuccessful."),
    DEVICE_OVERHEATED("Processing was paused because the device is overheating."),
    META_OAUTH_FAILED("Instagram/Facebook sign-in failed."),
    MISSING_IG_PERMISSIONS("Your Instagram account hasn't granted the permissions ReelBot needs to publish."),
    TOKEN_EXPIRED("Your Instagram connection expired. Please reconnect."),
    UPLOAD_FAILED("Uploading the Reel to Instagram failed."),
    META_PROCESSING_FAILED("Instagram couldn't process the uploaded video."),
    PUBLICATION_REJECTED("Instagram rejected the publish request."),
    CANCELLED_BY_USER("Cancelled."),
    UNKNOWN("Something failed and ReelBot doesn't have a specific reason.")
}
