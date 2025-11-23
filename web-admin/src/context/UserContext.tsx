import React, { createContext, useContext, useState, useEffect, ReactNode } from 'react';
import { api } from '../api/axiosConfig';
import i18n from '../i18n';

interface Workspace {
    id: string;
    title: string;
    role: string;
}

interface UserContextType {
  user: any;
  workspaces: Workspace[];
  currentWorkspace: Workspace | null;
  loading: boolean;
  refreshUser: () => Promise<void>;
  selectWorkspace: (ws: Workspace) => void;
  logout: () => void;
  
  // Computed flags based on CURRENT workspace
  isPartner: boolean; // Admin OR Manager
  isPartnerAdmin: boolean;
  isPartnerManager: boolean;
  isSuperAdmin: boolean;
  isNewUser: boolean; // Вообще нет ролей
}

const UserContext = createContext<UserContextType | undefined>(undefined);

export const UserProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<any>(null);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [currentWorkspace, setCurrentWorkspace] = useState<Workspace | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const cachedWs = localStorage.getItem('currentWorkspace');
    if (cachedWs) {
        setCurrentWorkspace(JSON.parse(cachedWs));
    }
    refreshUser();
  }, []);

  const refreshUser = async () => {
    try {
      const token = localStorage.getItem('accessToken');
      if (!token) {
          setLoading(false);
          return;
      }

      const res = await api.get('/client/me');
      const profile = res.data;
      setUser(profile);

      if (profile.language && profile.language !== i18n.language) {
          i18n.changeLanguage(profile.language);
      }

      const newWorkspaces = profile.workspaces || [];
      setWorkspaces(newWorkspaces);

      // Валидация текущего выбора
      if (currentWorkspace) {
          const stillExists = newWorkspaces.find((w: any) => w.id === currentWorkspace.id);
          if (!stillExists) {
              setCurrentWorkspace(null);
              localStorage.removeItem('currentWorkspace');
          }
      } else if (newWorkspaces.length === 1) {
          // Автовыбор, если всего один вариант
          selectWorkspace(newWorkspaces[0]);
      }

    } catch (e) {
      console.error("Failed to refresh user profile", e);
    } finally {
      setLoading(false);
    }
  };

  const selectWorkspace = (ws: Workspace) => {
      setCurrentWorkspace(ws);
      localStorage.setItem('currentWorkspace', JSON.stringify(ws));
  };

  const logout = () => {
      localStorage.clear();
      setUser(null);
      setWorkspaces([]);
      setCurrentWorkspace(null);
      window.location.href = '/login';
  };

  // Вычисляемые права зависят от ТЕКУЩЕГО выбора
  const role = currentWorkspace?.role;
  const isSuperAdmin = role === 'PLATFORM_SUPER_ADMIN';
  const isPartnerAdmin = role === 'PARTNER_ADMIN';
  const isPartnerManager = role === 'PARTNER_MANAGER';
  const isPartner = isPartnerAdmin || isPartnerManager;
  
  // isNewUser - если вообще нет воркспейсов (чистый лист)
  const isNewUser = workspaces.length === 0; 

  return (
    <UserContext.Provider value={{ 
        user, 
        workspaces, 
        currentWorkspace, 
        loading, 
        refreshUser, 
        selectWorkspace,
        logout,
        isPartner,
        isPartnerAdmin,
        isPartnerManager,
        isSuperAdmin, 
        isNewUser 
    }}>
      {children}
    </UserContext.Provider>
  );
};

export const useUser = () => {
  const context = useContext(UserContext);
  if (!context) throw new Error('useUser must be used within a UserProvider');
  return context;
};
