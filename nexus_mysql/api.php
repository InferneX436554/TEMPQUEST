<?php
declare(strict_types=1);
header('Content-Type: application/json; charset=utf-8');
require __DIR__ . '/config.php';
function respond(int $status, array $payload): never { http_response_code($status); echo json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_INVALID_UTF8_SUBSTITUTE); exit; }
$method = $_SERVER['REQUEST_METHOD'] ?? 'GET';
$route = $_GET['route'] ?? ($_GET['action'] ?? 'latest');
try {
    if ($route === 'register-device' && $method === 'POST') {
        $input = json_decode(file_get_contents('php://input'), true);
        $token = is_array($input) ? trim((string)($input['token'] ?? '')) : '';
        if ($token === '' || strlen($token) > 4096) respond(422, ['success'=>false,'message'=>'Token FCM inválido.']);
        $stmt = $pdo->prepare("INSERT INTO tempquest_devices (fcm_token, last_seen) VALUES (:token, NOW()) ON DUPLICATE KEY UPDATE last_seen=NOW()");
        $stmt->execute([':token'=>$token]);
        respond(200, ['success'=>true,'message'=>'Teléfono registrado para notificaciones.']);
    }
    if ($method === 'GET' && $route === 'status') {
        $stmt = $pdo->query("SELECT m.id_dispositivo, d.nombre AS dispositivo, d.ubicacion, m.temperatura, m.humedad, m.presion, DATE_FORMAT(m.fecha_hora,'%Y-%m-%d %H:%i:%s') AS fecha_hora FROM mediciones m INNER JOIN dispositivos d ON d.id=m.id_dispositivo INNER JOIN (SELECT id_dispositivo, MAX(id) AS max_id FROM mediciones GROUP BY id_dispositivo) x ON x.max_id=m.id WHERE d.activo=1 ORDER BY m.id_dispositivo");
        respond(200, ['success'=>true,'database_connected'=>true,'demo'=>false,'threshold'=>80,'latest'=>$stmt->fetchAll()]);
    }
    if ($method === 'GET' && $route === 'history') {
        $stmt = $pdo->query("SELECT id_dispositivo, temperatura AS temperature, humedad, presion, DATE_FORMAT(fecha_hora,'%Y-%m-%d %H:%i:%s') AS created_at FROM mediciones ORDER BY id DESC LIMIT 100");
        respond(200, ['success'=>true,'demo'=>false,'points'=>array_reverse($stmt->fetchAll())]);
    }
    if ($method === 'GET' && $route === 'alerts') {
        $stmt = $pdo->query("SELECT id, event_type, title, message, device, temperature, threshold, DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s') AS created_at FROM tempquest_alerts ORDER BY id DESC LIMIT 100");
        respond(200, ['success'=>true,'alerts'=>$stmt->fetchAll()]);
    }
    if ($method === 'GET' && $route === 'latest') {
        $limit = filter_input(INPUT_GET, 'limit', FILTER_VALIDATE_INT); $limit = ($limit && $limit > 0) ? min($limit,100) : 6;
        $stmt = $pdo->prepare("SELECT m.id,m.id_dispositivo,m.humedad,m.presion,m.temperatura,DATE_FORMAT(m.fecha_hora,'%Y-%m-%d %H:%i:%s') AS fecha_hora,d.nombre AS dispositivo,d.ubicacion FROM mediciones m INNER JOIN dispositivos d ON d.id=m.id_dispositivo ORDER BY m.fecha_hora DESC,m.id DESC LIMIT :limite");
        $stmt->bindValue(':limite',$limit,PDO::PARAM_INT); $stmt->execute(); respond(200,['success'=>true,'data'=>$stmt->fetchAll()]);
    }
    if ($method === 'POST' && $route === 'latest') {
        $input=json_decode(file_get_contents('php://input'),true); if(!is_array($input)) respond(400,['success'=>false,'message'=>'JSON inválido.']);
        $deviceId=filter_var($input['id_dispositivo']??1,FILTER_VALIDATE_INT); $temp=filter_var($input['temperatura']??null,FILTER_VALIDATE_FLOAT); $humidity=filter_var($input['humedad']??null,FILTER_VALIDATE_FLOAT); $pv=$input['presion']??null; $pressure=($pv===null||$pv==='')?null:filter_var($pv,FILTER_VALIDATE_FLOAT);
        if(!$deviceId||$temp===false||$humidity===false||$temp < -40||$temp>85||$humidity<0||$humidity>100||$pressure===false||($pressure!==null&&($pressure<300||$pressure>1200))) respond(422,['success'=>false,'message'=>'Datos inválidos o fuera de rango.']);
        $check=$pdo->prepare('SELECT id FROM dispositivos WHERE id=:id AND activo=1'); $check->execute([':id'=>$deviceId]); if(!$check->fetch()) respond(404,['success'=>false,'message'=>'Dispositivo inexistente o inactivo.']);
        $insert=$pdo->prepare('INSERT INTO mediciones (id_dispositivo,humedad,presion,temperatura) VALUES (:id,:h,:p,:t)'); $insert->bindValue(':id',$deviceId,PDO::PARAM_INT); $insert->bindValue(':h',$humidity); $insert->bindValue(':p',$pressure,$pressure===null?PDO::PARAM_NULL:PDO::PARAM_STR); $insert->bindValue(':t',$temp); $insert->execute(); respond(201,['success'=>true,'id'=>(int)$pdo->lastInsertId(),'message'=>'Medición guardada.']);
    }
    header('Allow: GET, POST'); respond(404,['success'=>false,'message'=>'Ruta no encontrada.']);
} catch(Throwable $e) { error_log($e->getMessage()); respond(500,['success'=>false,'message'=>'Error del servidor. Verificá la configuración y las tablas requeridas.']); }
