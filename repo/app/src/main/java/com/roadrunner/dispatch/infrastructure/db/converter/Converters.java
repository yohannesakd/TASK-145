package com.roadrunner.dispatch.infrastructure.db.converter;

import androidx.room.TypeConverter;

/**
 * Room type converters for the RoadRunner database.
 *
 * All current entity fields are primitives or Strings, so only a no-op
 * identity converter is needed to satisfy Room's annotation processor.
 * Add real @TypeConverter methods here when complex types (e.g. List,
 * Enum, Date) are introduced into entity fields.
 */
public class Converters {

    @TypeConverter
    public static Long fromLong(Long value) {
        return value;
    }
}
