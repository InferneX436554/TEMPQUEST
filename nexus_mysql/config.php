<?php
declare(strict_types=1);

$host = '127.0.0.1';
$dbname = 'sensores_db';
$username = 'root';
$password = ''; // En XAMPP suele estar vacío. Si configuraste una contraseña, colócala aquí.

$dsn = "mysql:host={$host};dbname={$dbname};charset=utf8mb4";

$options = [
    PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
    PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
    PDO::ATTR_EMULATE_PREPARES => false,
];

try {
    $pdo = new PDO($dsn, $username, $password, $options);
} catch (PDOException $e) {
    http_response_code(500);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode([
        'success' => false,
        'message' => 'No se pudo conectar con MySQL. Revisá config.php y que MySQL esté iniciado.'
    ], JSON_UNESCAPED_UNICODE);
    exit;
}
