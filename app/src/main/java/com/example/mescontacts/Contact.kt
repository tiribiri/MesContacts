package com.example.mescontacts

import android.os.Parcel
import android.os.Parcelable

/**
 * Un contact : nom, prénom, téléphone, e-mail et photo.
 * Parcelable (écrit à la main) pour pouvoir le transmettre d'une activité à l'autre via un Intent.
 * [photoPath] est le chemin d'un fichier JPEG dans le stockage interne (null = pas de photo).
 */
data class Contact(
    val id: Long,
    val nom: String,
    val prenom: String,
    val telephone: String,
    val email: String,
    val photoPath: String? = null
) : Parcelable {

    val nomComplet: String get() = "$nom $prenom".trim()

    private constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty(),
        parcel.readString().orEmpty(),
        parcel.readString()
    )

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(id)
        dest.writeString(nom)
        dest.writeString(prenom)
        dest.writeString(telephone)
        dest.writeString(email)
        dest.writeString(photoPath)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Contact> {
        override fun createFromParcel(parcel: Parcel): Contact = Contact(parcel)
        override fun newArray(size: Int): Array<Contact?> = arrayOfNulls(size)
    }
}
