package com.laudreader.data

import androidx.room.TypeConverter

class Converters {

    @TypeConverter
    fun fromArticleStatus(status: ArticleStatus): String = status.name

    @TypeConverter
    fun toArticleStatus(value: String): ArticleStatus = ArticleStatus.valueOf(value)
}
