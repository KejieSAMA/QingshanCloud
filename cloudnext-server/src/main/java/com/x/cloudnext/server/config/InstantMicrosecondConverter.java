package com.x.cloudnext.server.config;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/** MySQL DATETIME(6): explicitly truncate, rather than let the server round to the next instant. */
@Converter(autoApply = true)
public class InstantMicrosecondConverter implements AttributeConverter<Instant, Timestamp> {
    @Override
    public Timestamp convertToDatabaseColumn(Instant value) {
        return value == null ? null : Timestamp.from(value.truncatedTo(ChronoUnit.MICROS));
    }

    @Override
    public Instant convertToEntityAttribute(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
