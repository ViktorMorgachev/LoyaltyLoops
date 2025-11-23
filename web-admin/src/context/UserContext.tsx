import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { api } from '../api/axiosConfig';

interface UserContextType {
  workspaces: any[];
  loading: boolean;
  refreshUser: () => Promise<void>; // Метод для обновления данных с сервера
  isPartner: boolean;
  isSuperAdmin: boolean;
  isNewUser: boolean;
}

const UserContext = createContext<UserContextType | undefined>(undefined);

export const UserProvider = ({ children }: { children: ReactNode }) => {
  const [workspaces, setWorkspaces] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);

  // При старте или обновлении страницы пытаемся прочитать кеш или загрузить с сервера
  useEffect(() => {
    const cached = localStorage.getItem('workspaces');
    if (cached) {
      setWorkspaces(JSON.parse(cached));
      setLoading(false);
    }
    // Всегда обновляем актуальные данные в фоне
    refreshUser();
  }, []);

  const refreshUser = async () => {
    try {
      const token = localStorage.getItem('accessToken');
      if (!token) return; // Не залогинен

      const res = await api.get('/client/me');
      const newWorkspaces = res.data.workspaces || [];

      // Обновляем состояние React
      setWorkspaces(newWorkspaces);
      // Обновляем LocalStorage
      localStorage.setItem('workspaces', JSON.stringify(newWorkspaces));
    } catch (e) {
      console.error("Failed to refresh user profile", e);
    } finally {
      setLoading(false);
    }
  };

  // Вычисляемые права
  const isSuperAdmin = workspaces.some((w: any) => w.role === 'PLATFORM_SUPER_ADMIN');
  const isPartner = workspaces.some((w: any) => w.role === 'PARTNER_ADMIN');
  const isNewUser = !isSuperAdmin && !isPartner;

  return (
    <UserContext.Provider value={{ workspaces, loading, refreshUser, isPartner, isSuperAdmin, isNewUser }}>
      {children}
    </UserContext.Provider>
  );
};

export const useUser = () => {
  const context = useContext(UserContext);
  if (!context) throw new Error('useUser must be used within a UserProvider');
  return context;
};