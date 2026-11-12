package com.example.mescontacts

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.os.BundleCompat
import com.example.mescontacts.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ContactAdapter
    private val contacts = ArrayList<Contact>()

    // Reçoit le contact modifié renvoyé par DetailActivity (retour de résultat d'activité)
    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleDetailResult(result)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState != null) {
            BundleCompat.getParcelableArrayList(savedInstanceState, STATE_CONTACTS, Contact::class.java)
                ?.let { contacts.addAll(it) }
        } else {
            contacts.addAll(sampleContacts())
        }

        adapter = ContactAdapter(contacts)
        binding.listContacts.adapter = adapter
        binding.listContacts.emptyView = binding.txtEmpty

        // Intent explicite vers DetailActivity avec les données du contact sélectionné
        binding.listContacts.setOnItemClickListener { _, _, position, _ ->
            openDetail(contacts[position], position)
        }

        binding.toolbar.inflateMenu(R.menu.menu_main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_add) {
                openDetail(
                    Contact(System.currentTimeMillis(), "", "", "", "", null),
                    NEW_CONTACT
                )
                true
            } else false
        }
    }

    private fun openDetail(contact: Contact, position: Int) {
        val intent = Intent(this, DetailActivity::class.java).apply {
            putExtra(DetailActivity.EXTRA_CONTACT, contact)
            putExtra(DetailActivity.EXTRA_POSITION, position)
        }
        detailLauncher.launch(intent)
    }

    private fun handleDetailResult(result: ActivityResult) {
        // RESULT_CANCELED : l'utilisateur a annulé, la liste reste inchangée
        if (result.resultCode != RESULT_OK) return
        val data = result.data ?: return
        val updated = IntentCompat.getParcelableExtra(data, DetailActivity.EXTRA_CONTACT, Contact::class.java)
        if (updated == null) {
            Toast.makeText(this, R.string.msg_invalid_contact, Toast.LENGTH_SHORT).show()
            return
        }
        val position = data.getIntExtra(DetailActivity.EXTRA_POSITION, NEW_CONTACT)

        if (position in contacts.indices) {
            val old = contacts[position]
            if (old.photoPath != updated.photoPath) ImageUtils.deleteQuietly(old.photoPath)
            contacts[position] = updated
        } else {
            contacts.add(updated)
        }
        // Mise à jour immédiate de la liste (texte + photo)
        adapter.notifyDataSetChanged()
        Toast.makeText(this, R.string.msg_saved, Toast.LENGTH_SHORT).show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putParcelableArrayList(STATE_CONTACTS, contacts)
    }

    private fun sampleContacts() = listOf(
        Contact(1, "Dupont", "Claire", "06 12 34 56 78", "claire.dupont@example.com"),
        Contact(2, "Martin", "Thomas", "06 98 45 63 20", "thomas.martin@example.com"),
        Contact(3, "Bernard", "Sophie", "06 60 71 24 32", "sophie.bernard@example.com"),
        Contact(4, "Petit", "Lucas", "07 11 22 85 44", "lucas.petit@example.com"),
        Contact(5, "Moreau", "Julien", "06 55 44 93 22", "julien.moreau@example.com")
    )

    companion object {
        private const val STATE_CONTACTS = "state_contacts"
        const val NEW_CONTACT = -1
    }
}
