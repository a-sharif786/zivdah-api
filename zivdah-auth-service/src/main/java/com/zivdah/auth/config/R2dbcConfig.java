package com.zivdah.auth.config;

import com.zivdah.auth.enums.Role;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.lang.NonNull;

import java.util.List;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(
                PostgresDialect.INSTANCE,
                List.of(new RoleWritingConverter(), new RoleReadingConverter())
        );
    }

    @WritingConverter
    static class RoleWritingConverter implements Converter<Role, String> {
        @Override
        public String convert(@NonNull Role role) {
            return role.name();
        }
    }

    @ReadingConverter
    static class RoleReadingConverter implements Converter<String, Role> {
        @Override
        public Role convert(@NonNull String source) {
            return Role.valueOf(source);
        }
    }
}
