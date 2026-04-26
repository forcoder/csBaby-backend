package com.csbaby.kefu.data.local

import androidx.room.TypeConverter
import com.csbaby.kefu.domain.model.MatchType
import com.csbaby.kefu.domain.model.RuleTargetType

class Converters {
    @TypeConverter
    fun fromRuleTargetType(value: RuleTargetType): String = value.name

    @TypeConverter
    fun toRuleTargetType(value: String): RuleTargetType =
        try { RuleTargetType.valueOf(value) } catch (e: Exception) { RuleTargetType.ALL }

    @TypeConverter
    fun fromMatchType(value: MatchType): String = value.name

    @TypeConverter
    fun toMatchType(value: String): MatchType =
        try { MatchType.valueOf(value) } catch (e: Exception) { MatchType.CONTAINS }
}
