import React from 'react';
import { Box, Typography, Paper, List, ListItem, ListItemText, Divider, Accordion, AccordionSummary, AccordionDetails } from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { useTranslation } from 'react-i18next';

export const AboutPage = () => {
  const { t } = useTranslation();

  const milestones = [
    t('about.items.architecture'),
    t('about.items.security'),
    t('about.items.localization'),
    t('about.items.soon'),
  ];

  const loyaltyPrograms = [
    {
      key: 'tiered',
      title: t('about.loyalty_types.tiered_title'),
      description: t('about.loyalty_types.tiered_desc')
    },
    {
      key: 'visits',
      title: t('about.loyalty_types.visits_title'),
      description: t('about.loyalty_types.visits_desc')
    },
    {
      key: 'hybrid',
      title: t('about.loyalty_types.hybrid_title'),
      description: t('about.loyalty_types.hybrid_desc')
    }
  ];

  const faqItems = [
    {
      key: 'cashback',
      question: t('about.faq.cashback_q'),
      answer: t('about.faq.cashback_a')
    },
    {
      key: 'visits',
      question: t('about.faq.visits_q'),
      answer: t('about.faq.visits_a')
    },
    {
      key: 'downgrade',
      question: t('about.faq.downgrade_q'),
      answer: t('about.faq.downgrade_a')
    },
    {
      key: 'staff',
      question: t('about.faq.staff_q'),
      answer: t('about.faq.staff_a')
    }
  ];

  return (
    <Box maxWidth="md">
      <Typography variant="h4" fontWeight="bold" gutterBottom>
        {t('about.title')}
      </Typography>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6">{t('about.section_mission')}</Typography>
        <Typography variant="body1" color="text.secondary">
          {t('about.mission_text')}
        </Typography>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6">{t('about.section_focus')}</Typography>
        <List>
          {milestones.map((item, idx) => (
            <React.Fragment key={idx}>
              <ListItem>
                <ListItemText primary={item} />
              </ListItem>
              {idx !== milestones.length - 1 && <Divider component="li" />}
            </React.Fragment>
          ))}
        </List>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>{t('about.loyalty_types.title')}</Typography>
        <List>
          {loyaltyPrograms.map((program, idx) => (
            <React.Fragment key={program.key}>
              <ListItem alignItems="flex-start">
                <ListItemText
                  primary={program.title}
                  secondary={<Typography variant="body2" color="text.secondary">{program.description}</Typography>}
                />
              </ListItem>
              {idx !== loyaltyPrograms.length - 1 && <Divider component="li" />}
            </React.Fragment>
          ))}
        </List>
      </Paper>

      <Paper sx={{ p: 3, mb: 3 }}>
        <Typography variant="h6" gutterBottom>{t('about.faq.title')}</Typography>
        {faqItems.map((item) => (
          <Accordion key={item.key} disableGutters>
            <AccordionSummary expandIcon={<ExpandMoreIcon />}>
              <Typography fontWeight="bold">{item.question}</Typography>
            </AccordionSummary>
            <AccordionDetails>
              <Typography variant="body2" color="text.secondary">
                {item.answer}
              </Typography>
            </AccordionDetails>
          </Accordion>
        ))}
      </Paper>

      <Paper sx={{ p: 3 }}>
        <Typography variant="h6">{t('about.section_contact')}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t('about.contact_text')}
        </Typography>
      </Paper>
    </Box>
  );
};

