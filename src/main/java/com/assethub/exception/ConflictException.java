package com.assethub.exception;

import org.springframework.http.HttpStatus;
import java.util.LinkedHashMap;
import java.util.Map;

public class ConflictException extends AppException {
    private final Map<String, Object> details = new LinkedHashMap<>();

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }

    public ConflictException(String message, String heldBy, Long allocationId, String hint) {
        super(message, HttpStatus.CONFLICT);
        if (heldBy != null) details.put("held_by", heldBy);
        if (allocationId != null) details.put("allocation_id", allocationId);
        if (hint != null) details.put("hint", hint);
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
