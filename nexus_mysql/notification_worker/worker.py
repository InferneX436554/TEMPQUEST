import configparser, json, os, time
from datetime import datetime
import mysql.connector
import firebase_admin
from firebase_admin import credentials, messaging

BASE = os.path.dirname(os.path.abspath(__file__))
config = configparser.ConfigParser()
config.read(os.path.join(BASE, 'config.ini'), encoding='utf-8')
mc = config['mysql']; fc = config['firebase']; wc = config['worker']
state_path = os.path.join(BASE, wc.get('state_file', 'worker_state.json'))
interval = max(2, wc.getint('interval_seconds', 5))
cred_path = fc.get('service_account', 'service-account.json')
if not os.path.isabs(cred_path): cred_path = os.path.join(BASE, cred_path)
if not os.path.isfile(cred_path): raise SystemExit('Falta el JSON de cuenta de servicio de Firebase: ' + cred_path)
if not firebase_admin._apps: firebase_admin.initialize_app(credentials.Certificate(cred_path))

def connect():
    return mysql.connector.connect(host=mc.get('host','127.0.0.1'), port=mc.getint('port',3306), database=mc.get('database','sensores_db'), user=mc.get('user','root'), password=mc.get('password',''), autocommit=True)

def load_state():
    try:
        with open(state_path, encoding='utf-8') as f: return json.load(f)
    except (OSError, ValueError): return None

def save_state(state):
    tmp=state_path+'.tmp'
    with open(tmp,'w',encoding='utf-8') as f: json.dump(state,f,ensure_ascii=False)
    os.replace(tmp,state_path)

def snapshot(db):
    cur=db.cursor(dictionary=True)
    cur.execute("SELECT id,id_dispositivo,humedad,presion,temperatura,DATE_FORMAT(fecha_hora,'%Y-%m-%d %H:%i:%s') fecha_hora FROM mediciones ORDER BY id DESC LIMIT 150")
    measurements={str(r['id']): {k:(str(v) if v is not None else None) for k,v in r.items()} for r in cur.fetchall()}
    cur.execute("SELECT id,nombre,ubicacion,tipo,activo,DATE_FORMAT(fecha_registro,'%Y-%m-%d %H:%i:%s') fecha_registro FROM dispositivos ORDER BY id")
    devices={str(r['id']): {k:(str(v) if v is not None else None) for k,v in r.items()} for r in cur.fetchall()}
    cur.close(); return {'measurements':measurements,'devices':devices}

def tokens(db):
    cur=db.cursor(); cur.execute('SELECT fcm_token FROM tempquest_devices'); result=[r[0] for r in cur.fetchall()]; cur.close(); return result

def record_alert(db, event_type, title, body, device, temperature=None):
    cur=db.cursor(); cur.execute('INSERT INTO tempquest_alerts (event_type,title,message,device,temperature,threshold,created_at) VALUES (%s,%s,%s,%s,%s,80,NOW())',(event_type,title,body,device,temperature)); cur.close()

def send(db, data):
    active=tokens(db)
    if not active: return
    for token in active:
        try: messaging.send(messaging.Message(data={k:str(v) for k,v in data.items()}, token=token, android=messaging.AndroidConfig(priority='high')))
        except Exception as exc:
            print('FCM error:',exc,flush=True)

def main():
    print('TempQuest worker iniciado. Revisión cada',interval,'segundos.',flush=True)
    while True:
        db=None
        try:
            db=connect(); current=snapshot(db); previous=load_state()
            if previous is not None:
                oldm=previous.get('measurements',{}); newm=current['measurements']
                for mid,row in newm.items():
                    old=oldm.get(mid)
                    if old is None or old != row:
                        device_id=str(row['id_dispositivo']); cur=db.cursor(dictionary=True); cur.execute('SELECT nombre,ubicacion FROM dispositivos WHERE id=%s',(device_id,)); d=cur.fetchone() or {}; cur.close()
                        name=d.get('nombre') or ('Dispositivo '+device_id); temp=row.get('temperatura'); humidity=row.get('humedad'); pressure=row.get('presion')
                        changed='Nueva medición' if old is None else 'Medición modificada'
                        body=f"{name}: temperatura {temp} °C, humedad {humidity} %, presión {pressure if pressure is not None else 'sin dato'} hPa"
                        record_alert(db,'measurement',changed,body,name,float(temp) if temp is not None else None)
                        send(db,{'type':'database_change','title':changed,'body':body,'device':name,'device_id':device_id,'measurement_id':mid,'temperature':temp or '', 'humidity':humidity or '', 'pressure':pressure or ''})
                oldd=previous.get('devices',{}); newd=current['devices']
                for did,row in newd.items():
                    old=oldd.get(did)
                    if old is not None and old != row:
                        name=row.get('nombre') or ('Dispositivo '+did); changes=[]
                        for key in ('nombre','ubicacion','tipo','activo'):
                            if old.get(key)!=row.get(key): changes.append(f"{key}: {old.get(key)} → {row.get(key)}")
                        body=f"{name}: "+', '.join(changes)
                        record_alert(db,'device','Dispositivo modificado',body,name)
                        send(db,{'type':'database_change','title':'Dispositivo modificado','body':body,'device':name,'device_id':did})
            save_state(current)
        except Exception as exc: print('Error de revisión:',exc,flush=True)
        finally:
            if db is not None:
                try: db.close()
                except Exception: pass
        time.sleep(interval)

if __name__=='__main__': main()
