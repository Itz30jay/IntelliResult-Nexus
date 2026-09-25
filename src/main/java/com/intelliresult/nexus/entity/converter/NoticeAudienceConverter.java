package com.intelliresult.nexus.entity.converter;

import com.intelliresult.nexus.entity.enums.UserRole;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Converts between notices.audience's native MySQL SET('ADMIN','TEACHER',
 * 'STUDENT') column and a type-safe Set&lt;UserRole&gt; on the Notice entity.
 * JPA has no first-class mapping for MySQL's SET type, so this is a plain
 * AttributeConverter rather than a Hibernate-specific UserType - the
 * standard, portable way to bridge a column shape JPA doesn't know natively.
 * MySQL always returns a SET column's value with its members in the order
 * they were declared on the column (ADMIN,TEACHER,STUDENT here), not
 * insertion order, so a LinkedHashSet built directly from the split string
 * preserves a stable, predictable iteration order for free.
 */
@Converter
public class NoticeAudienceConverter implements AttributeConverter<Set<UserRole>, String> {

    @Override
    public String convertToDatabaseColumn(Set<UserRole> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        return attribute.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    @Override
    public Set<UserRole> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new LinkedHashSet<>();
        }
        return Arrays.stream(dbData.split(","))
                .map(UserRole::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
