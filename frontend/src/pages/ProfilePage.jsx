import { useEffect, useState } from 'react'
import { errorMessage } from '../api/http'
import { authApi } from '../api/services'
import Alert from '../components/Alert'
import Spinner from '../components/Spinner'
import { useAuth } from '../context/AuthContext'

const blankAddress = { line1: '', line2: '', city: '', state: '', postalCode: '', country: 'IN', defaultAddress: false }

export default function ProfilePage() {
  const { user, setUser } = useAuth()
  const [profile, setProfile] = useState({ firstName: user.firstName, lastName: user.lastName, phone: user.phone || '' })
  const [profileMsg, setProfileMsg] = useState(null)

  const [addresses, setAddresses] = useState(null)
  const [editing, setEditing] = useState(null) // null = closed, {} = new, {id,...} = edit
  const [addrError, setAddrError] = useState('')

  const loadAddresses = () =>
    authApi.addresses().then(setAddresses).catch((e) => { setAddresses([]); setAddrError(errorMessage(e)) })

  useEffect(() => {
    loadAddresses()
  }, [])

  const saveProfile = async (e) => {
    e.preventDefault()
    setProfileMsg(null)
    try {
      setUser(await authApi.updateMe({ ...profile, phone: profile.phone || undefined }))
      setProfileMsg({ type: 'success', text: 'Profile updated.' })
    } catch (err) {
      setProfileMsg({ type: 'error', text: errorMessage(err) })
    }
  }

  const saveAddress = async (e) => {
    e.preventDefault()
    setAddrError('')
    const { id, ...body } = editing
    try {
      if (id) await authApi.updateAddress(id, body)
      else await authApi.addAddress(body)
      setEditing(null)
      await loadAddresses()
    } catch (err) {
      setAddrError(errorMessage(err))
    }
  }

  const removeAddress = async (id) => {
    if (!window.confirm('Delete this address?')) return
    setAddrError('')
    try {
      await authApi.deleteAddress(id)
      await loadAddresses()
    } catch (err) {
      setAddrError(errorMessage(err))
    }
  }

  const setAddr = (field) => (e) =>
    setEditing({ ...editing, [field]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section>
        <h1 className="mb-4 text-2xl font-bold">My profile</h1>
        <form onSubmit={saveProfile} className="card space-y-4 p-5">
          {profileMsg && <Alert type={profileMsg.type}>{profileMsg.text}</Alert>}
          <div>
            <label className="label">Email</label>
            <input className="input bg-slate-100" value={user.email} disabled />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label" htmlFor="fn">First name</label>
              <input id="fn" className="input" required value={profile.firstName}
                onChange={(e) => setProfile({ ...profile, firstName: e.target.value })} />
            </div>
            <div>
              <label className="label" htmlFor="ln">Last name</label>
              <input id="ln" className="input" required value={profile.lastName}
                onChange={(e) => setProfile({ ...profile, lastName: e.target.value })} />
            </div>
          </div>
          <div>
            <label className="label" htmlFor="ph">Phone</label>
            <input id="ph" className="input" value={profile.phone}
              onChange={(e) => setProfile({ ...profile, phone: e.target.value })} />
          </div>
          <button className="btn-primary">Save changes</button>
        </form>
      </section>

      <section>
        <div className="mb-4 flex items-center justify-between">
          <h2 className="text-2xl font-bold">Addresses</h2>
          {!editing && (
            <button className="btn-secondary" onClick={() => setEditing({ ...blankAddress })}>Add address</button>
          )}
        </div>
        <Alert>{addrError}</Alert>

        {editing && (
          <form onSubmit={saveAddress} className="card mb-4 space-y-3 p-5">
            <h3 className="font-semibold">{editing.id ? 'Edit address' : 'New address'}</h3>
            <input className="input" placeholder="Address line 1" required value={editing.line1} onChange={setAddr('line1')} />
            <input className="input" placeholder="Address line 2" value={editing.line2 || ''} onChange={setAddr('line2')} />
            <div className="grid grid-cols-2 gap-3">
              <input className="input" placeholder="City" required value={editing.city} onChange={setAddr('city')} />
              <input className="input" placeholder="State" required value={editing.state} onChange={setAddr('state')} />
              <input className="input" placeholder="Postal code" required value={editing.postalCode} onChange={setAddr('postalCode')} />
              <input className="input" placeholder="Country" required value={editing.country} onChange={setAddr('country')} />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={editing.defaultAddress} onChange={setAddr('defaultAddress')} />
              Make this my default address
            </label>
            <div className="flex gap-2">
              <button className="btn-primary">Save</button>
              <button type="button" className="btn-secondary" onClick={() => setEditing(null)}>Cancel</button>
            </div>
          </form>
        )}

        {addresses === null ? (
          <Spinner />
        ) : addresses.length === 0 ? (
          !editing && <p className="text-sm text-slate-500">No saved addresses yet.</p>
        ) : (
          <div className="space-y-3">
            {addresses.map((a) => (
              <div key={a.id} className="card flex items-start justify-between gap-3 p-4">
                <div className="text-sm">
                  <p className="font-medium">
                    {a.line1}{a.line2 ? `, ${a.line2}` : ''}
                    {a.defaultAddress && (
                      <span className="ml-2 rounded-full bg-indigo-100 px-2 py-0.5 text-xs text-indigo-700">Default</span>
                    )}
                  </p>
                  <p className="text-slate-600">{a.city}, {a.state} {a.postalCode}, {a.country}</p>
                </div>
                <div className="flex gap-3 text-sm">
                  <button className="text-indigo-600 hover:underline" onClick={() => setEditing({ ...a })}>Edit</button>
                  <button className="text-red-600 hover:underline" onClick={() => removeAddress(a.id)}>Delete</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
