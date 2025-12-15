import { createContext, useContext, useState, useEffect } from 'react';
import type { ReactNode } from 'react';
import { api } from '../api/axiosConfig';
import i18n from '../i18n';
import { Analytics } from '../utils/analytics';

export interface Workspace {
    id: string;
    title: string;
    role: string;
    requirePin: boolean;
}

interface UserContextType {
  user: any;
  workspaces: Workspace[];
  currentWorkspace: Workspace | null;
  loading: boolean;
  refreshUser: () => Promise<any | null>;
  selectWorkspace: (ws: Workspace) => void;
  logout: () => void;
  
  // Computed flags based on CURRENT workspace
  isPartner: boolean; // Admin OR Manager
  isPartnerAdmin: boolean;
  isPartnerManager: boolean;
  isSuperAdmin: boolean;
  isSuperManager: boolean;
  isPlatformManager: boolean;
  isPlatformStaff: boolean;
  isNewUser: boolean; // Вообще нет ролей
}

const CURRENT_WORKSPACE_KEY = 'currentWorkspaceId';

const UserContext = createContext<UserContextType | undefined>(undefined);

export const UserProvider = ({ children }: { children: ReactNode }) => {
  const [user, setUser] = useState<any>(null);
  const [workspaces, setWorkspaces] = useState<Workspace[]>([]);
  const [currentWorkspace, setCurrentWorkspace] = useState<Workspace | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
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

      // --- ANALYTICS IDENTIFY ---
      if (profile?.id) {
          Analytics.identify(profile.id, {
              phone: profile.phoneNumber,
              email: profile.email,
              roles: profile.workspaces?.map((w: any) => w.role).join(',')
          });
      }

      if (profile.language) {
          if (profile.language !== i18n.language) {
              i18n.changeLanguage(profile.language);
          }
          api.defaults.headers.common['Accept-Language'] = profile.language;
          localStorage.setItem('lang', profile.language);
      }

      const newWorkspaces = profile.workspaces || [];
      setWorkspaces(newWorkspaces);

      // Валидация текущего выбора
      if (currentWorkspace) {
          const stillExists = newWorkspaces.find((w: any) => w.id === currentWorkspace.id);
          if (!stillExists) {
              setCurrentWorkspace(null);
          }
      }

      const cachedId = typeof window !== 'undefined' ? localStorage.getItem(CURRENT_WORKSPACE_KEY) : null;
      if (!currentWorkspace && cachedId) {
          const match = newWorkspaces.find((w: any) => w.id === cachedId);
          if (match) {
              setCurrentWorkspace(match);
          } else {
              localStorage.removeItem(CURRENT_WORKSPACE_KEY);
          }
      }
      return profile;

    } catch (e) {
      console.error("Failed to refresh user profile", e);
      return null;
    } finally {
      setLoading(false);
    }
  };

  const selectWorkspace = (ws: Workspace) => {
      setCurrentWorkspace(ws);
      if (typeof window !== 'undefined') {
          localStorage.setItem(CURRENT_WORKSPACE_KEY, ws.id);
          localStorage.removeItem('currentWorkspace'); // cleanup legacy key
      }
  };

  const logout = () => {
      const preservedLang = localStorage.getItem('lang') || i18n.language;

      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('workspaces');
      localStorage.removeItem(CURRENT_WORKSPACE_KEY);

      if (preservedLang) {
          localStorage.setItem('lang', preservedLang);
          i18n.changeLanguage(preservedLang);
          api.defaults.headers.common['Accept-Language'] = preservedLang;
      }

      setUser(null);
      setWorkspaces([]);
      setCurrentWorkspace(null);

      // Use replace to prevent "Back" navigation to protected pages
      window.location.replace('/login');
  };

  // Вычисляемые права зависят от ТЕКУЩЕГО выбора
  const role = currentWorkspace?.role;
  const isSuperAdmin = role === 'PLATFORM_SUPER_ADMIN';
  const isSuperManager = role === 'PLATFORM_SUPER_MANAGER';
  const isPlatformManager = role === 'PLATFORM_MANAGER';
  const isPartnerAdmin = role === 'PARTNER_ADMIN';
  const isPartnerManager = role === 'PARTNER_MANAGER';
  const isPartner = isPartnerAdmin || isPartnerManager;
  const isPlatformStaff = isSuperAdmin || isPlatformManager || isSuperManager;
  
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
        isSuperManager,
        isPlatformManager,
        isPlatformStaff,
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
