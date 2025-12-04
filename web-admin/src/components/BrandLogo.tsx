import { Box, useTheme } from '@mui/material';
import LocalOfferIcon from '@mui/icons-material/LocalOffer';
import FavoriteIcon from '@mui/icons-material/Favorite';

export const BrandLogo = ({ size = 64 }: { size?: number }) => {
    const theme = useTheme();
    const tagSize = size;
    const heartSize = size * 0.45;

    return (
        <Box sx={{ position: 'relative', width: tagSize, height: tagSize, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {/* Tag Icon as background base */}
            <LocalOfferIcon 
                sx={{ 
                    fontSize: tagSize, 
                    color: 'primary.main',
                    transform: 'rotate(-45deg)',
                    filter: 'drop-shadow(0 4px 6px rgba(37, 99, 235, 0.3))'
                }} 
            />
            
            {/* Heart Icon overlay */}
            <FavoriteIcon 
                sx={{ 
                    fontSize: heartSize, 
                    color: 'white',
                    position: 'absolute',
                    top: '50%',
                    left: '50%',
                    transform: 'translate(-50%, -50%) rotate(-45deg) translateY(-10%)', // Adjust center visually
                    zIndex: 1
                }} 
            />
        </Box>
    );
};

