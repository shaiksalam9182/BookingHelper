package com.mine.bookinghelper.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "general_details")
data class GeneralDetails(
    @PrimaryKey val id: Int = 1,
    val email: String = "",
    val city: String = "",
    val state: String = "",
    val country: String = "",
    val pincode: String = ""
)

@Entity(tableName = "person_details")
data class PersonDetail(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val personIndex: Int, 
    val name: String = "",
    val age: String = "",
    val gender: String = "", 
    val idType: String = "", 
    val idNumber: String = ""
)
