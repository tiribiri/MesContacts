package com.example.mescontacts

import android.graphics.Bitmap
import android.util.LruCache
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.example.mescontacts.databinding.ItemContactBinding

/**
 * Adapter personnalisé du ListView : chaque ligne affiche la photo, le nom,
 * le prénom et le numéro de téléphone du contact.
 */
class ContactAdapter(private val contacts: List<Contact>) : BaseAdapter() {

    // Les chemins de photos sont uniques (un nouveau fichier à chaque changement de photo)
    private val cache = LruCache<String, Bitmap>(40)

    override fun getCount(): Int = contacts.size

    override fun getItem(position: Int): Contact = contacts[position]

    override fun getItemId(position: Int): Long = contacts[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val binding: ItemContactBinding
        val view: View
        if (convertView == null) {
            binding = ItemContactBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            view = binding.root
            view.tag = binding
        } else {
            view = convertView
            binding = convertView.tag as ItemContactBinding
        }

        val contact = getItem(position)
        binding.txtName.text = contact.nomComplet
        binding.txtPhone.text = contact.telephone

        val bitmap = contact.photoPath?.let { path ->
            cache.get(path) ?: ImageUtils.decodeScaled(path, 160)?.also { cache.put(path, it) }
        }
        if (bitmap != null) {
            binding.imgPhoto.setImageBitmap(bitmap)
        } else {
            binding.imgPhoto.setImageResource(R.drawable.placeholder_avatar)
        }
        return view
    }
}
