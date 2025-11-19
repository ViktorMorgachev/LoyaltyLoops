export const getErrorMessage = (error: any): string => {
  // 1. Если сервер прислал ответ (например, 400 или 500)
  if (error.response) {
    // Пытаемся достать сообщение из JSON (наш ApiMessage)
    const serverMessage = error.response.data?.message;
    if (serverMessage) return serverMessage;

    // Если JSON нет, смотрим на статус
    switch (error.response.status) {
        case 400: return "Некорректный запрос (400)";
        case 401: return "Сессия истекла, войдите снова";
        case 403: return "Доступ запрещен";
        case 404: return "Ресурс не найден (404)";
        case 500: return "Ошибка сервера (500)";
        default: return `Ошибка: ${error.response.statusText}`;
    }
  }
  // 2. Если запрос ушел, но ответа нет (сервер выключен или нет интернета)
  else if (error.request) {
    return "Нет соединения с сервером";
  }
  // 3. Ошибка при настройке запроса
  else {
    return error.message || "Произошла неизвестная ошибка";
  }
};